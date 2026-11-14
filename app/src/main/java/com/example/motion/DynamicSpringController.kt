package com.example.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.example.ui.theme.LocalMotionPolicy

/**
 * Bridge over `androidx.dynamicanimation.animation.SpringAnimation` using [FloatValueHolder]
 * and configurable [SpringForce] stiffness & damping for smooth, interruptible Compose physics.
 */
@Stable
class DynamicSpringController(
    initialValue: Float,
    minimumVisibleChange: Float = DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE,
) {
    private val valueHolder = FloatValueHolder(initialValue)
    private var _value by mutableFloatStateOf(initialValue)
    val value: Float get() = _value

    private val springForce = SpringForce(initialValue).apply {
        stiffness = SpringForce.STIFFNESS_MEDIUM
        dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
    }

    private val springAnimation = SpringAnimation(valueHolder).apply {
        spring = springForce
        this.minimumVisibleChange = minimumVisibleChange
    }

    private val updateListener = DynamicAnimation.OnAnimationUpdateListener { _, newValue, _ ->
        _value = newValue
    }

    init {
        springAnimation.addUpdateListener(updateListener)
    }

    fun configure(config: SpringPhysicsConfig) {
        springForce.stiffness = config.stiffness.coerceIn(10f, 10_000f)
        springForce.dampingRatio = config.dampingRatio.coerceIn(0.1f, 2.0f)
    }

    fun animateTo(
        targetValue: Float,
        initialVelocity: Float? = null,
        immediateSnap: Boolean = false,
    ) {
        if (immediateSnap) {
            snapTo(targetValue)
            return
        }
        if (initialVelocity != null) {
            springAnimation.setStartVelocity(initialVelocity)
        }
        springAnimation.animateToFinalPosition(targetValue)
    }

    fun snapTo(targetValue: Float) {
        springAnimation.cancel()
        valueHolder.value = targetValue
        springForce.finalPosition = targetValue
        _value = targetValue
    }

    fun dispose() {
        springAnimation.cancel()
        springAnimation.removeUpdateListener(updateListener)
    }
}

/**
 * Remembers an `androidx.dynamicanimation.animation.SpringAnimation` backed float state that
 * automatically tracks [targetValue] using configurable [stiffness] and [dampingRatio]
 * (or snaps cleanly when reduced motion is enabled).
 */
@Composable
fun rememberDynamicSpringValue(
    targetValue: Float,
    stiffness: Float,
    dampingRatio: Float,
    minimumVisibleChange: Float = DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE,
    key: Any? = Unit,
): State<Float> {
    val policy = LocalMotionPolicy.current
    val controller = remember(key) {
        DynamicSpringController(
            initialValue = targetValue,
            minimumVisibleChange = minimumVisibleChange,
        )
    }

    DisposableEffect(controller) {
        onDispose { controller.dispose() }
    }

    LaunchedEffect(controller, targetValue, stiffness, dampingRatio, policy.reducedMotion) {
        controller.configure(SpringPhysicsConfig(stiffness = stiffness, dampingRatio = dampingRatio))
        controller.animateTo(
            targetValue = targetValue,
            immediateSnap = policy.reducedMotion,
        )
    }

    return remember(controller) {
        object : State<Float> {
            override val value: Float get() = controller.value
        }
    }
}
