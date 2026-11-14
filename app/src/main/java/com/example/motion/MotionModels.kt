package com.example.motion

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

enum class PerformanceProfile(val label: String) {
    LOW("Low · Power & Jank Safe"),
    BALANCED("Balanced · Smooth"),
    HIGH("High · Peak Refresh"),
}

@Serializable
enum class MotionModePreference(val label: String) {
    AUTO("Auto (Adaptive)"),
    BALANCED("Balanced"),
    BATTERY_SAFE("Low / Power Safe"),
}

enum class RefreshTier(val targetHz: Int, val label: String) {
    HZ_60(60, "60 Hz"),
    HZ_90(90, "90 Hz"),
    HZ_120(120, "120 Hz"),
    HZ_144(144, "144 Hz");

    companion object {
        fun fromHz(hz: Float): RefreshTier = when {
            hz >= 135f -> HZ_144
            hz >= 110f -> HZ_120
            hz >= 82f -> HZ_90
            else -> HZ_60
        }
    }
}

enum class ThermalLevel(val label: String) {
    NOMINAL("Nominal"),
    LIGHT("Warm"),
    MODERATE("Moderate Throttle"),
    SEVERE("Severe Throttle"),
    UNKNOWN("Unavailable"),
}

/**
 * Configurable physics parameters passed directly to AndroidX [androidx.dynamicanimation.animation.SpringForce].
 */
data class SpringPhysicsConfig(
    val stiffness: Float = 1100f,
    val dampingRatio: Float = 0.85f,
)

data class FrameHealthSnapshot(
    val sampleCount: Int = 0,
    val missedFramesCount: Int = 0,
    val jankPercent: Float = 0f,
    val p95FrameMs: Float = 0f,
    val frameBudgetMs: Float = 16.67f,
)

data class MotionTelemetry(
    val supportedRefreshRatesHz: List<Int> = listOf(60),
    val activeRefreshRateHz: Float = 60f,
    val refreshTier: RefreshTier = RefreshTier.HZ_60,
    val activeProfile: PerformanceProfile = PerformanceProfile.BALANCED,
    val thermalLevel: ThermalLevel = ThermalLevel.UNKNOWN,
    val batterySaverActive: Boolean = false,
    val reducedMotionActive: Boolean = false,
    val modePreference: MotionModePreference = MotionModePreference.AUTO,
    val configuredStiffness: Float = 1100f,
    val configuredDamping: Float = 0.85f,
    val frameHealth: FrameHealthSnapshot = FrameHealthSnapshot(),
    val adaptationReason: String = "Initial baseline",
)

/**
 * Motion and visual complexity policy derived from [PerformanceProfile], display refresh rate,
 * and configurable user spring physics parameters.
 */
data class MotionPolicy(
    val profile: PerformanceProfile,
    val targetRefreshRateHz: Float,
    val reducedMotion: Boolean,
    val shortDurationMs: Int,
    val mediumDurationMs: Int,
    val emphasisDurationMs: Int,
    val gentleStiffness: Float,
    val gentleDamping: Float,
    val bouncyStiffness: Float,
    val bouncyDamping: Float,
    val quickStiffness: Float,
    val quickDamping: Float,
    val toolbarSpring: SpringPhysicsConfig,
    val tabCardSpring: SpringPhysicsConfig,
    val enableRichTransitions: Boolean,
    val enablePressScale: Boolean,
    val cardElevationDp: Float,
    val translucentChromeAlpha: Float,
) {
    fun <T> gentleSpring(): FiniteAnimationSpec<T> = when {
        reducedMotion -> snap()
        profile == PerformanceProfile.LOW -> tween(durationMillis = shortDurationMs)
        else -> spring(dampingRatio = gentleDamping, stiffness = gentleStiffness)
    }

    fun <T> bouncySpring(): FiniteAnimationSpec<T> = when {
        reducedMotion -> snap()
        profile == PerformanceProfile.LOW -> tween(durationMillis = shortDurationMs)
        else -> spring(dampingRatio = bouncyDamping, stiffness = bouncyStiffness)
    }

    fun <T> quickSpring(): FiniteAnimationSpec<T> = when {
        reducedMotion -> snap()
        profile == PerformanceProfile.LOW -> tween(durationMillis = shortDurationMs)
        else -> spring(dampingRatio = quickDamping, stiffness = quickStiffness)
    }

    fun colorSpec(): FiniteAnimationSpec<Color> = when {
        reducedMotion -> snap()
        profile == PerformanceProfile.LOW -> tween(durationMillis = 90)
        else -> tween(durationMillis = mediumDurationMs)
    }

    companion object {
        fun forProfile(
            profile: PerformanceProfile,
            targetRefreshRateHz: Float,
            reducedMotion: Boolean,
            configuredStiffness: Float = 1100f,
            configuredDamping: Float = 0.85f,
        ): MotionPolicy {
            val baseStiffness = configuredStiffness.coerceIn(200f, 3500f)
            val baseDamping = configuredDamping.coerceIn(0.45f, 1.0f)

            if (reducedMotion) {
                val snapSpring = SpringPhysicsConfig(stiffness = 10_000f, dampingRatio = 1.0f)
                return MotionPolicy(
                    profile = PerformanceProfile.LOW,
                    targetRefreshRateHz = targetRefreshRateHz,
                    reducedMotion = true,
                    shortDurationMs = 0,
                    mediumDurationMs = 0,
                    emphasisDurationMs = 0,
                    gentleStiffness = Spring.StiffnessHigh,
                    gentleDamping = Spring.DampingRatioNoBouncy,
                    bouncyStiffness = Spring.StiffnessHigh,
                    bouncyDamping = Spring.DampingRatioNoBouncy,
                    quickStiffness = Spring.StiffnessHigh,
                    quickDamping = Spring.DampingRatioNoBouncy,
                    toolbarSpring = snapSpring,
                    tabCardSpring = snapSpring,
                    enableRichTransitions = false,
                    enablePressScale = false,
                    cardElevationDp = 0f,
                    translucentChromeAlpha = 1.0f,
                )
            }
            return when (profile) {
                PerformanceProfile.LOW -> {
                    // Tighter damping without bounce to minimize settle time on low/throttled devices
                    val safeDamping = maxOf(baseDamping, 0.96f)
                    val fastStiffness = maxOf(baseStiffness * 1.45f, 1600f)
                    MotionPolicy(
                        profile = PerformanceProfile.LOW,
                        targetRefreshRateHz = minOf(targetRefreshRateHz, 60f),
                        reducedMotion = false,
                        shortDurationMs = 110,
                        mediumDurationMs = 150,
                        emphasisDurationMs = 180,
                        gentleStiffness = fastStiffness,
                        gentleDamping = safeDamping,
                        bouncyStiffness = fastStiffness,
                        bouncyDamping = safeDamping,
                        quickStiffness = Spring.StiffnessHigh,
                        quickDamping = Spring.DampingRatioNoBouncy,
                        toolbarSpring = SpringPhysicsConfig(stiffness = fastStiffness, dampingRatio = safeDamping),
                        tabCardSpring = SpringPhysicsConfig(stiffness = fastStiffness, dampingRatio = safeDamping),
                        enableRichTransitions = false,
                        enablePressScale = false,
                        cardElevationDp = 0f,
                        translucentChromeAlpha = 1.0f,
                    )
                }
                PerformanceProfile.BALANCED -> {
                    val toolbarSpring = SpringPhysicsConfig(
                        stiffness = baseStiffness,
                        dampingRatio = maxOf(baseDamping, 0.85f),
                    )
                    val tabCardSpring = SpringPhysicsConfig(
                        stiffness = (baseStiffness * 0.92f).coerceIn(200f, 3500f),
                        dampingRatio = baseDamping,
                    )
                    MotionPolicy(
                        profile = PerformanceProfile.BALANCED,
                        targetRefreshRateHz = targetRefreshRateHz.coerceIn(60f, 120f),
                        reducedMotion = false,
                        shortDurationMs = 140,
                        mediumDurationMs = 210,
                        emphasisDurationMs = 270,
                        gentleStiffness = baseStiffness * 0.65f,
                        gentleDamping = maxOf(baseDamping, 0.92f),
                        bouncyStiffness = baseStiffness,
                        bouncyDamping = baseDamping,
                        quickStiffness = maxOf(baseStiffness * 1.35f, Spring.StiffnessHigh),
                        quickDamping = maxOf(baseDamping, 0.9f),
                        toolbarSpring = toolbarSpring,
                        tabCardSpring = tabCardSpring,
                        enableRichTransitions = true,
                        enablePressScale = true,
                        cardElevationDp = 2.5f,
                        translucentChromeAlpha = 0.94f,
                    )
                }
                PerformanceProfile.HIGH -> {
                    val refreshScale = if (targetRefreshRateHz >= 135f) 1.12f else 1.04f
                    val scaledStiffness = (baseStiffness * refreshScale).coerceIn(250f, 4000f)
                    MotionPolicy(
                        profile = PerformanceProfile.HIGH,
                        targetRefreshRateHz = targetRefreshRateHz,
                        reducedMotion = false,
                        shortDurationMs = 120,
                        mediumDurationMs = 190,
                        emphasisDurationMs = 250,
                        gentleStiffness = scaledStiffness * 0.72f,
                        gentleDamping = maxOf(baseDamping, 0.9f),
                        bouncyStiffness = scaledStiffness,
                        bouncyDamping = baseDamping,
                        quickStiffness = maxOf(scaledStiffness * 1.4f, Spring.StiffnessHigh),
                        quickDamping = maxOf(baseDamping, 0.88f),
                        toolbarSpring = SpringPhysicsConfig(
                            stiffness = scaledStiffness,
                            dampingRatio = maxOf(baseDamping, 0.84f),
                        ),
                        tabCardSpring = SpringPhysicsConfig(
                            stiffness = scaledStiffness * 0.94f,
                            dampingRatio = baseDamping,
                        ),
                        enableRichTransitions = true,
                        enablePressScale = true,
                        cardElevationDp = 4f,
                        translucentChromeAlpha = 0.92f,
                    )
                }
            }
        }
    }
}
