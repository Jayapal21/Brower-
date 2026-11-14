package com.example.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.dynamicanimation.animation.DynamicAnimation
import com.example.motion.MotionPolicy
import com.example.motion.PerformanceProfile
import com.example.motion.rememberDynamicSpringValue

val LocalReducedMotion = compositionLocalOf { false }

val LocalMotionPolicy = compositionLocalOf {
    MotionPolicy.forProfile(
        profile = PerformanceProfile.BALANCED,
        targetRefreshRateHz = 60f,
        reducedMotion = false,
    )
}

@Composable
fun rememberReducedMotionPreference(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }
}

object ObscuraMotion {
    fun <T> gentleSpring(reducedMotion: Boolean = false): FiniteAnimationSpec<T> =
        if (reducedMotion) snap()
        else spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        )

    fun <T> bouncySpring(reducedMotion: Boolean = false): FiniteAnimationSpec<T> =
        if (reducedMotion) snap()
        else spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium,
        )

    fun <T> quickSpring(reducedMotion: Boolean = false): FiniteAnimationSpec<T> =
        if (reducedMotion) snap()
        else spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessHigh,
        )

    fun colorSpec(reducedMotion: Boolean = false): FiniteAnimationSpec<Color> =
        if (reducedMotion) snap() else tween(durationMillis = ObscuraMotionTokens.DURATION_MEDIUM_MS)
}

/**
 * Hardware-accelerated press scale powered by `androidx.dynamicanimation.animation.SpringAnimation`
 * using configurable stiffness and damping from [LocalMotionPolicy].
 */
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = ObscuraMotionTokens.PRESSED_SCALE_CARD,
    enabled: Boolean = true,
): Modifier = composed {
    val policy = LocalMotionPolicy.current
    val active = enabled && !policy.reducedMotion && policy.enablePressScale
    val isPressed by interactionSource.collectIsPressedAsState()
    val effectiveStiffness = maxOf(policy.tabCardSpring.stiffness * 1.25f, 1500f)
    val effectiveDamping = policy.tabCardSpring.dampingRatio
    val scale by rememberDynamicSpringValue(
        targetValue = if (isPressed && active) pressedScale else 1f,
        stiffness = effectiveStiffness,
        dampingRatio = effectiveDamping,
        minimumVisibleChange = DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE,
        key = interactionSource,
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
