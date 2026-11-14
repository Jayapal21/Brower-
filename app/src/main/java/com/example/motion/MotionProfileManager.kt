package com.example.motion

/**
 * Pure deterministic state machine that classifies device capability and live runtime signals
 * into a stable [PerformanceProfile] using hysteresis and cooldown windows to avoid oscillation.
 */
class MotionProfileManager(
    private val downgradeSustainedMs: Long = 2_000L,
    private val upgradeCooldownMs: Long = 6_000L,
    private val minSamplesForJankDecision: Int = 24,
) {

    data class MotionSignals(
        val supportedMaxHz: Float,
        val activeHz: Float,
        val jankPercent: Float,
        val sampleCount: Int,
        val thermalLevel: ThermalLevel,
        val batterySaverActive: Boolean,
        val reducedMotionActive: Boolean,
        val modePreference: MotionModePreference,
        val timestampMs: Long,
    )

    data class Decision(
        val profile: PerformanceProfile,
        val reason: String,
    )

    private var currentProfile: PerformanceProfile? = null
    private var lastTransitionMs: Long = 0L
    private var jankElevatedSinceMs: Long? = null
    private var healthySinceMs: Long? = null

    fun evaluate(signals: MotionSignals): Decision {
        val hardwareCeiling = hardwareMaxProfile(signals.supportedMaxHz)

        // Immediate safety / accessibility overrides (bypass hysteresis)
        if (signals.reducedMotionActive) {
            return transitionTo(PerformanceProfile.LOW, signals.timestampMs, "System reduced motion enabled")
        }
        if (signals.modePreference == MotionModePreference.BATTERY_SAFE) {
            return transitionTo(PerformanceProfile.LOW, signals.timestampMs, "User selected Low / Power Safe mode")
        }
        if (signals.batterySaverActive) {
            return transitionTo(PerformanceProfile.LOW, signals.timestampMs, "Device Battery Saver active")
        }
        if (signals.thermalLevel == ThermalLevel.SEVERE) {
            return transitionTo(PerformanceProfile.LOW, signals.timestampMs, "Severe thermal throttling detected")
        }

        val effectiveCeiling = when {
            signals.modePreference == MotionModePreference.BALANCED ->
                minProfile(hardwareCeiling, PerformanceProfile.BALANCED)
            signals.thermalLevel == ThermalLevel.MODERATE ->
                minProfile(hardwareCeiling, PerformanceProfile.BALANCED)
            else -> hardwareCeiling
        }

        val existing = currentProfile
        if (existing == null) {
            val initial = effectiveCeiling
            return transitionTo(
                initial,
                signals.timestampMs,
                "Display capability ${signals.supportedMaxHz.toInt()} Hz",
            )
        }

        // Enforce lowered ceiling immediately if thermal or user mode tightened
        if (existing > effectiveCeiling) {
            val reason = when {
                signals.thermalLevel == ThermalLevel.MODERATE -> "Thermal throttling capped profile"
                signals.modePreference == MotionModePreference.BALANCED -> "User selected Balanced mode"
                else -> "Display refresh ceiling changed"
            }
            return transitionTo(effectiveCeiling, signals.timestampMs, reason)
        }

        val hasEnoughSamples = signals.sampleCount >= minSamplesForJankDecision
        val highJankThreshold = if (existing == PerformanceProfile.HIGH) 18f else 32f
        val isJankElevated = hasEnoughSamples && signals.jankPercent >= highJankThreshold

        if (isJankElevated) {
            healthySinceMs = null
            val started = jankElevatedSinceMs ?: signals.timestampMs.also { jankElevatedSinceMs = it }
            if (signals.timestampMs - started >= downgradeSustainedMs) {
                val next = when (existing) {
                    PerformanceProfile.HIGH -> PerformanceProfile.BALANCED
                    PerformanceProfile.BALANCED -> PerformanceProfile.LOW
                    PerformanceProfile.LOW -> PerformanceProfile.LOW
                }
                if (next != existing) {
                    return transitionTo(
                        next,
                        signals.timestampMs,
                        "Sustained frame deadline misses (${signals.jankPercent.toInt()}% jank)",
                    )
                }
            }
            return Decision(existing, "Monitoring elevated frame drops")
        } else {
            jankElevatedSinceMs = null
        }

        // Potential upgrade when conditions are cleanly healthy for cooldown window
        val healthyForUpgrade = !hasEnoughSamples || when (existing) {
            PerformanceProfile.LOW -> signals.jankPercent <= 10f
            PerformanceProfile.BALANCED -> signals.jankPercent <= 6f
            PerformanceProfile.HIGH -> true
        }

        if (existing < effectiveCeiling && healthyForUpgrade &&
            (signals.thermalLevel == ThermalLevel.NOMINAL ||
                signals.thermalLevel == ThermalLevel.LIGHT ||
                signals.thermalLevel == ThermalLevel.UNKNOWN)
        ) {
            val startedHealthy = healthySinceMs ?: signals.timestampMs.also { healthySinceMs = it }
            val elapsedHealthy = signals.timestampMs - startedHealthy
            val elapsedSinceLastSwitch = signals.timestampMs - lastTransitionMs
            if (elapsedHealthy >= upgradeCooldownMs && elapsedSinceLastSwitch >= upgradeCooldownMs) {
                val stepUp = when (existing) {
                    PerformanceProfile.LOW -> minProfile(PerformanceProfile.BALANCED, effectiveCeiling)
                    PerformanceProfile.BALANCED -> minProfile(PerformanceProfile.HIGH, effectiveCeiling)
                    PerformanceProfile.HIGH -> PerformanceProfile.HIGH
                }
                if (stepUp != existing) {
                    return transitionTo(stepUp, signals.timestampMs, "Frame pacing stabilized; restoring motion richness")
                }
            }
        } else {
            healthySinceMs = null
        }

        return Decision(existing, stableReason(existing, signals.supportedMaxHz))
    }

    private fun transitionTo(
        target: PerformanceProfile,
        timestampMs: Long,
        reason: String,
    ): Decision {
        if (currentProfile != target) {
            currentProfile = target
            lastTransitionMs = timestampMs
            jankElevatedSinceMs = null
            healthySinceMs = null
        }
        return Decision(target, reason)
    }

    private fun stableReason(profile: PerformanceProfile, maxHz: Float): String = when (profile) {
        PerformanceProfile.HIGH -> "Smooth ${maxHz.toInt()} Hz headroom"
        PerformanceProfile.BALANCED -> if (maxHz < 110f) "Balanced for ${maxHz.toInt()} Hz display" else "Balanced adaptive pacing"
        PerformanceProfile.LOW -> "Lightweight motion active"
    }

    companion object {
        fun hardwareMaxProfile(supportedMaxHz: Float): PerformanceProfile = when {
            supportedMaxHz >= 110f -> PerformanceProfile.HIGH
            else -> PerformanceProfile.BALANCED
        }

        private fun minProfile(a: PerformanceProfile, b: PerformanceProfile): PerformanceProfile =
            if (a.ordinal <= b.ordinal) a else b
    }
}
