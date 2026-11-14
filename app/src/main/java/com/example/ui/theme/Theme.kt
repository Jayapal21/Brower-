package com.example.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.motion.MotionPolicy
import com.example.motion.PerformanceProfile

@Immutable
data class ObscuraExtendedColors(
    val isDark: Boolean,
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val privateAccent: Color,
    val privateContainer: Color,
    val onPrivateContainer: Color,
    val rimHighlightTop: Color,
    val rimHighlightBottom: Color,
    val shadowTint: Color,
    val heroAuraPrimary: Color,
    val heroAuraSecondary: Color,
    val faviconBackdrop: Color,
)

private val LightExtendedColors = ObscuraExtendedColors(
    isDark = false,
    success = StatusSuccessLight,
    successContainer = StatusSuccessContainerLight,
    onSuccessContainer = StatusOnSuccessContainerLight,
    warning = StatusWarningLight,
    warningContainer = StatusWarningContainerLight,
    onWarningContainer = StatusOnWarningContainerLight,
    privateAccent = PrivateAccentLight,
    privateContainer = PrivateContainerLight,
    onPrivateContainer = PrivateOnContainerLight,
    rimHighlightTop = Color(0xFFFFFFFF),
    rimHighlightBottom = LightBorderSubtle,
    shadowTint = LightShadowTint,
    heroAuraPrimary = Color(0x222563EB),
    heroAuraSecondary = Color(0x160EA5E9),
    faviconBackdrop = LightSurfaceLowest,
)

private val DarkExtendedColors = ObscuraExtendedColors(
    isDark = true,
    success = StatusSuccessDark,
    successContainer = StatusSuccessContainerDark,
    onSuccessContainer = StatusOnSuccessContainerDark,
    warning = StatusWarningDark,
    warningContainer = StatusWarningContainerDark,
    onWarningContainer = StatusOnWarningContainerDark,
    privateAccent = PrivateAccentDark,
    privateContainer = PrivateContainerDark,
    onPrivateContainer = PrivateOnContainerDark,
    rimHighlightTop = Color(0x1EFFFFFF),
    rimHighlightBottom = Color(0x0AFFFFFF),
    shadowTint = DarkShadowTint,
    heroAuraPrimary = Color(0x2436D6AE),
    heroAuraSecondary = Color(0x182563EB),
    faviconBackdrop = DarkTextPrimary,
)

val LocalObscuraColors = staticCompositionLocalOf { DarkExtendedColors }

private val ObscuraDarkScheme = darkColorScheme(
    primary = DarkAccent,
    onPrimary = DarkOnAccent,
    primaryContainer = DarkAccentContainer,
    onPrimaryContainer = DarkOnAccentContainer,
    secondary = DarkAccent,
    onSecondary = DarkOnAccent,
    secondaryContainer = DarkSurfaceHigh,
    onSecondaryContainer = DarkTextPrimary,
    tertiary = PrivateAccentDark,
    onTertiary = DarkOnAccent,
    tertiaryContainer = PrivateContainerDark,
    onTertiaryContainer = PrivateOnContainerDark,
    error = StatusErrorDark,
    onError = DarkTextPrimary,
    errorContainer = StatusErrorContainerDark,
    onErrorContainer = StatusOnErrorContainerDark,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceHigh,
    onSurfaceVariant = DarkTextSecondary,
    surfaceContainerLowest = DarkSurfaceLowest,
    surfaceContainerLow = DarkSurfaceLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceHigh,
    surfaceContainerHighest = DarkSurfaceHighest,
    outline = DarkBorder,
    outlineVariant = DarkBorderSubtle,
)

private val ObscuraLightScheme = lightColorScheme(
    primary = LightAccent,
    onPrimary = LightOnAccent,
    primaryContainer = LightAccentContainer,
    onPrimaryContainer = LightOnAccentContainer,
    secondary = LightAccent,
    onSecondary = LightOnAccent,
    secondaryContainer = LightSurfaceHigh,
    onSecondaryContainer = LightTextPrimary,
    tertiary = PrivateAccentLight,
    onTertiary = LightOnAccent,
    tertiaryContainer = PrivateContainerLight,
    onTertiaryContainer = PrivateOnContainerLight,
    error = StatusErrorLight,
    onError = LightOnAccent,
    errorContainer = StatusErrorContainerLight,
    onErrorContainer = StatusOnErrorContainerLight,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceHigh,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainerLowest = LightSurfaceLowest,
    surfaceContainerLow = LightSurfaceLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceHigh,
    surfaceContainerHighest = LightSurfaceHighest,
    outline = LightBorder,
    outlineVariant = LightBorderSubtle,
)

val ObscuraShapes = Shapes(
    extraSmall = RoundedCornerShape(ObscuraRadii.xs),
    small = RoundedCornerShape(ObscuraRadii.sm),
    medium = RoundedCornerShape(ObscuraRadii.md),
    large = RoundedCornerShape(ObscuraRadii.lg),
    extraLarge = RoundedCornerShape(ObscuraRadii.capsule),
)

@Composable
private fun animateSchemeColor(target: Color, spec: FiniteAnimationSpec<Color>): Color {
    val color by animateColorAsState(
        targetValue = target,
        animationSpec = spec,
        label = "theme_color",
    )
    return color
}

@Composable
fun ObscuraTheme(
    darkTheme: Boolean = true,
    motionPolicy: MotionPolicy = MotionPolicy.forProfile(
        profile = PerformanceProfile.BALANCED,
        targetRefreshRateHz = 60f,
        reducedMotion = false,
    ),
    content: @Composable () -> Unit,
) {
    val systemReduced = rememberReducedMotionPreference()
    val effectivePolicy = if (systemReduced && !motionPolicy.reducedMotion) {
        MotionPolicy.forProfile(PerformanceProfile.LOW, motionPolicy.targetRefreshRateHz, reducedMotion = true)
    } else {
        motionPolicy
    }
    val targetScheme: ColorScheme = if (darkTheme) ObscuraDarkScheme else ObscuraLightScheme
    val targetExtended = if (darkTheme) DarkExtendedColors else LightExtendedColors

    val colorScheme = if (effectivePolicy.reducedMotion || effectivePolicy.profile == PerformanceProfile.LOW) {
        targetScheme
    } else {
        val spec = effectivePolicy.colorSpec()
        targetScheme.copy(
            primary = animateSchemeColor(targetScheme.primary, spec),
            onPrimary = animateSchemeColor(targetScheme.onPrimary, spec),
            primaryContainer = animateSchemeColor(targetScheme.primaryContainer, spec),
            onPrimaryContainer = animateSchemeColor(targetScheme.onPrimaryContainer, spec),
            background = animateSchemeColor(targetScheme.background, spec),
            onBackground = animateSchemeColor(targetScheme.onBackground, spec),
            surface = animateSchemeColor(targetScheme.surface, spec),
            onSurface = animateSchemeColor(targetScheme.onSurface, spec),
            surfaceVariant = animateSchemeColor(targetScheme.surfaceVariant, spec),
            onSurfaceVariant = animateSchemeColor(targetScheme.onSurfaceVariant, spec),
            surfaceContainerLowest = animateSchemeColor(targetScheme.surfaceContainerLowest, spec),
            surfaceContainerLow = animateSchemeColor(targetScheme.surfaceContainerLow, spec),
            surfaceContainer = animateSchemeColor(targetScheme.surfaceContainer, spec),
            surfaceContainerHigh = animateSchemeColor(targetScheme.surfaceContainerHigh, spec),
            surfaceContainerHighest = animateSchemeColor(targetScheme.surfaceContainerHighest, spec),
            outline = animateSchemeColor(targetScheme.outline, spec),
            outlineVariant = animateSchemeColor(targetScheme.outlineVariant, spec),
        )
    }

    CompositionLocalProvider(
        LocalReducedMotion provides effectivePolicy.reducedMotion,
        LocalMotionPolicy provides effectivePolicy,
        LocalObscuraColors provides targetExtended,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ObscuraTypography,
            shapes = ObscuraShapes,
            content = content,
        )
    }
}
