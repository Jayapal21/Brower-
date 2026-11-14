package com.example.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Centralized design tokens ensuring visual consistency, soft depth,
 * and Material 3 proportions across all Obscura screens and overlays.
 */
object ObscuraSpacing {
    val xxs = 4.dp
    val xs = 6.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
}

object ObscuraRadii {
    val xs = 10.dp
    val sm = 14.dp
    val md = 18.dp
    val lg = 24.dp
    val xl = 28.dp
    val capsule = 32.dp
    val pill = 50
}

object ObscuraSizing {
    val minTouchTarget = 48.dp
    val toolbarHeight = 58.dp
    val addressCapsuleHeight = 44.dp
    val homeSearchHeight = 56.dp
    val buttonHeight = 48.dp
    val iconSmall = 16.dp
    val iconStandard = 20.dp
    val iconToolbar = 21.dp
    val iconEmpty = 28.dp
    val badgeSize = 40.dp
    val borderHairline = 0.85.dp
    val borderThin = 1.dp
    val borderActive = 1.75.dp
    val maxContentWidthCompact = 680.dp
    val maxContentWidthExpanded = 780.dp
}

object ObscuraElevation {
    val flat = 0.dp
    val subtle = 2.dp
    val card = 3.dp
    val chrome = 5.dp
    val floating = 9.dp
}

object ObscuraMotionTokens {
    const val DURATION_INSTANT_MS = 0
    const val DURATION_SHORT_MS = 140
    const val DURATION_MEDIUM_MS = 220
    const val DURATION_EMPHASIS_MS = 300

    const val PRESSED_SCALE_ICON = 0.90f
    const val PRESSED_SCALE_BUTTON = 0.945f
    const val PRESSED_SCALE_CARD = 0.978f
    const val PRESSED_SCALE_TILE = 0.955f
}
