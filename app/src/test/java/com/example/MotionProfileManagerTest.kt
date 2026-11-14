package com.example

import com.example.motion.MotionModePreference
import com.example.motion.MotionPolicy
import com.example.motion.MotionProfileManager
import com.example.motion.MotionProfileManager.MotionSignals
import com.example.motion.PerformanceProfile
import com.example.motion.RefreshTier
import com.example.motion.ThermalLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionProfileManagerTest {

    private fun signals(
        maxHz: Float = 120f,
        activeHz: Float = maxHz,
        jankPct: Float = 0f,
        samples: Int = 40,
        thermal: ThermalLevel = ThermalLevel.NOMINAL,
        batterySaver: Boolean = false,
        reducedMotion: Boolean = false,
        mode: MotionModePreference = MotionModePreference.AUTO,
        ts: Long = 1000L,
    ) = MotionSignals(
        supportedMaxHz = maxHz,
        activeHz = activeHz,
        jankPercent = jankPct,
        sampleCount = samples,
        thermalLevel = thermal,
        batterySaverActive = batterySaver,
        reducedMotionActive = reducedMotion,
        modePreference = mode,
        timestampMs = ts,
    )

    @Test
    fun refreshTier_classifiesAllDisplayRatesWithoutOverScaling60Hz() {
        assertEquals(RefreshTier.HZ_60, RefreshTier.fromHz(60f))
        assertEquals(RefreshTier.HZ_90, RefreshTier.fromHz(90f))
        assertEquals(RefreshTier.HZ_120, RefreshTier.fromHz(120f))
        assertEquals(RefreshTier.HZ_144, RefreshTier.fromHz(144f))

        assertEquals(PerformanceProfile.BALANCED, MotionProfileManager.hardwareMaxProfile(60f))
        assertEquals(PerformanceProfile.BALANCED, MotionProfileManager.hardwareMaxProfile(90f))
        assertEquals(PerformanceProfile.HIGH, MotionProfileManager.hardwareMaxProfile(120f))
        assertEquals(PerformanceProfile.HIGH, MotionProfileManager.hardwareMaxProfile(144f))
    }

    @Test
    fun hysteresis_ignoresTransientJankSpikeAndDowngradesOnSustainedJank() {
        val manager = MotionProfileManager(
            downgradeSustainedMs = 2_000L,
            upgradeCooldownMs = 6_000L,
            minSamplesForJankDecision = 24,
        )

        // Baseline on healthy 120Hz device -> HIGH
        assertEquals(PerformanceProfile.HIGH, manager.evaluate(signals(maxHz = 120f, ts = 1_000L)).profile)

        // Single jank spike at t=2000ms (< 2000ms sustained) stays HIGH
        assertEquals(PerformanceProfile.HIGH, manager.evaluate(signals(maxHz = 120f, jankPct = 28f, ts = 2_000L)).profile)
        assertEquals(PerformanceProfile.HIGH, manager.evaluate(signals(maxHz = 120f, jankPct = 28f, ts = 3_500L)).profile)

        // Sustained >= 2000ms jank downgrades HIGH -> BALANCED
        assertEquals(PerformanceProfile.BALANCED, manager.evaluate(signals(maxHz = 120f, jankPct = 28f, ts = 4_100L)).profile)

        // Sustained severe jank downgrades BALANCED -> LOW
        assertEquals(PerformanceProfile.BALANCED, manager.evaluate(signals(maxHz = 120f, jankPct = 40f, ts = 5_000L)).profile)
        assertEquals(PerformanceProfile.LOW, manager.evaluate(signals(maxHz = 120f, jankPct = 40f, ts = 7_100L)).profile)
    }

    @Test
    fun hysteresis_requiresCooldownBeforeSteppingBackUp() {
        val manager = MotionProfileManager(
            downgradeSustainedMs = 1_000L,
            upgradeCooldownMs = 5_000L,
            minSamplesForJankDecision = 20,
        )

        manager.evaluate(signals(maxHz = 120f, ts = 0L))
        manager.evaluate(signals(maxHz = 120f, jankPct = 25f, ts = 1_000L))
        assertEquals(PerformanceProfile.BALANCED, manager.evaluate(signals(maxHz = 120f, jankPct = 25f, ts = 2_100L)).profile)

        // Immediately healthy at t=3000 -> still BALANCED until 5s cooldown passes
        assertEquals(PerformanceProfile.BALANCED, manager.evaluate(signals(maxHz = 120f, jankPct = 1f, ts = 3_000L)).profile)
        assertEquals(PerformanceProfile.BALANCED, manager.evaluate(signals(maxHz = 120f, jankPct = 1f, ts = 7_500L)).profile)

        // After >= 5000ms healthy & >= 5000ms since last switch -> restores HIGH
        assertEquals(PerformanceProfile.HIGH, manager.evaluate(signals(maxHz = 120f, jankPct = 1f, ts = 8_200L)).profile)
    }

    @Test
    fun thermalAndBatterySaver_enforceImmediateSafeFallback() {
        val manager = MotionProfileManager()
        assertEquals(PerformanceProfile.HIGH, manager.evaluate(signals(maxHz = 144f, ts = 0L)).profile)

        // Moderate thermal caps at BALANCED
        assertEquals(
            PerformanceProfile.BALANCED,
            manager.evaluate(signals(maxHz = 144f, thermal = ThermalLevel.MODERATE, ts = 500L)).profile,
        )

        // Severe thermal drops to LOW immediately
        assertEquals(
            PerformanceProfile.LOW,
            manager.evaluate(signals(maxHz = 144f, thermal = ThermalLevel.SEVERE, ts = 800L)).profile,
        )

        // Even with nominal thermal, Battery Saver locks LOW
        assertEquals(
            PerformanceProfile.LOW,
            manager.evaluate(signals(maxHz = 144f, thermal = ThermalLevel.NOMINAL, batterySaver = true, ts = 1_000L)).profile,
        )
    }

    @Test
    fun motionPolicy_adaptsVisualComplexityPerProfile() {
        val low = MotionPolicy.forProfile(PerformanceProfile.LOW, targetRefreshRateHz = 120f, reducedMotion = false)
        assertEquals(60f, low.targetRefreshRateHz, 0.01f)
        assertFalse(low.enableRichTransitions)
        assertFalse(low.enablePressScale)
        assertEquals(0f, low.cardElevationDp, 0.01f)

        val high = MotionPolicy.forProfile(PerformanceProfile.HIGH, targetRefreshRateHz = 144f, reducedMotion = false)
        assertEquals(144f, high.targetRefreshRateHz, 0.01f)
        assertTrue(high.enableRichTransitions)
        assertTrue(high.enablePressScale)
        assertTrue(high.cardElevationDp > 0f)
    }

    @Test
    fun motionPolicy_propagatesConfigurableStiffnessAndDamping() {
        val balanced = MotionPolicy.forProfile(
            profile = PerformanceProfile.BALANCED,
            targetRefreshRateHz = 90f,
            reducedMotion = false,
            configuredStiffness = 1800f,
            configuredDamping = 0.65f,
        )
        assertEquals(1800f, balanced.toolbarSpring.stiffness, 0.01f)
        assertEquals(0.85f, balanced.toolbarSpring.dampingRatio, 0.01f)
        assertEquals(0.65f, balanced.tabCardSpring.dampingRatio, 0.01f)

        // LOW profile enforces crisp damping without bounce even if user set bouncy damping
        val low = MotionPolicy.forProfile(
            profile = PerformanceProfile.LOW,
            targetRefreshRateHz = 60f,
            reducedMotion = false,
            configuredStiffness = 500f,
            configuredDamping = 0.50f,
        )
        assertTrue(low.tabCardSpring.dampingRatio >= 0.96f)
        assertTrue(low.toolbarSpring.stiffness >= 1600f)
    }
}
