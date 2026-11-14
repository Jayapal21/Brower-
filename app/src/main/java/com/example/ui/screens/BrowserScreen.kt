package com.example.ui.screens

import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.example.R
import com.example.data.AddressSuggestion
import com.example.data.BookmarkItem
import com.example.data.BrowserDestination
import com.example.data.BrowserWorkspaceState
import com.example.data.HistoryItem
import com.example.data.ObscuraSettings
import com.example.data.WebsiteShortcut
import com.example.motion.rememberDynamicSpringValue
import com.example.ui.ObscuraViewModel
import com.example.ui.components.DomainMonogramBadge
import com.example.ui.components.StatusPill
import com.example.ui.components.TactileButton
import com.example.ui.components.TactileCardSurface
import com.example.ui.components.TactileIconButton
import com.example.ui.components.TactileOutlinedButton
import com.example.ui.components.TactileTextButton
import com.example.ui.components.obscuraDepthShadow
import com.example.ui.components.obscuraRimBorder
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalMotionPolicy
import com.example.ui.theme.LocalObscuraColors
import com.example.ui.theme.ObscuraElevation
import com.example.ui.theme.ObscuraMotionTokens
import com.example.ui.theme.ObscuraRadii
import com.example.ui.theme.ObscuraSizing
import com.example.ui.theme.ObscuraSpacing
import com.example.ui.theme.pressScale
import java.net.URI
import kotlin.math.roundToInt

@Composable
fun BrowserScreen(
    viewModel: ObscuraViewModel,
    workspace: BrowserWorkspaceState,
    settings: ObscuraSettings,
    shortcuts: List<WebsiteShortcut>,
    bookmarks: List<BookmarkItem>,
    history: List<HistoryItem>,
    suggestions: List<AddressSuggestion>,
    shortcutFavicons: Map<String, Bitmap> = emptyMap(),
    pullRefreshProgress: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val policy = LocalMotionPolicy.current
    val extColors = LocalObscuraColors.current
    val activeTab = workspace.activeTab
    val showToolbar = workspace.toolbarVisible || activeTab.isHome || workspace.isFindInPageOpen

    val tabAccent = if (activeTab.isPrivate) extColors.privateAccent else MaterialTheme.colorScheme.primary
    val tabAccentContainer = if (activeTab.isPrivate) extColors.privateContainer else MaterialTheme.colorScheme.primaryContainer
    val tabOnAccentContainer = if (activeTab.isPrivate) extColors.onPrivateContainer else MaterialTheme.colorScheme.onPrimaryContainer

    val hostLabel = remember(activeTab.url, activeTab.isHome) {
        if (activeTab.isHome) {
            "Search or enter URL"
        } else {
            runCatching { URI(activeTab.url).host?.removePrefix("www.") ?: activeTab.url }
                .getOrDefault(activeTab.url)
        }
    }

    val toolbarSpringFraction by rememberDynamicSpringValue(
        targetValue = if (showToolbar) 1f else 0f,
        stiffness = policy.toolbarSpring.stiffness,
        dampingRatio = policy.toolbarSpring.dampingRatio,
        minimumVisibleChange = DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE,
        key = "toolbar_visibility_spring",
    )

    val forwardFraction by rememberDynamicSpringValue(
        targetValue = if (activeTab.canGoForward && !activeTab.isHome) 1f else 0f,
        stiffness = policy.toolbarSpring.stiffness,
        dampingRatio = policy.toolbarSpring.dampingRatio,
        minimumVisibleChange = DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE,
        key = "toolbar_forward_spring",
    )

    val findBarFraction by rememberDynamicSpringValue(
        targetValue = if (workspace.isFindInPageOpen && !activeTab.isHome) 1f else 0f,
        stiffness = policy.toolbarSpring.stiffness,
        dampingRatio = policy.toolbarSpring.dampingRatio,
        minimumVisibleChange = DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE,
        key = "find_bar_spring",
    )

    val animatedProgress by rememberDynamicSpringValue(
        targetValue = (activeTab.progress / 100f).coerceIn(0.05f, 1f),
        stiffness = SpringForce.STIFFNESS_LOW,
        dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY,
        minimumVisibleChange = DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE,
        key = "toolbar_progress_spring",
    )

    val animatedPull by rememberDynamicSpringValue(
        targetValue = pullRefreshProgress.coerceIn(0f, 1.3f),
        stiffness = policy.toolbarSpring.stiffness,
        dampingRatio = policy.toolbarSpring.dampingRatio,
        minimumVisibleChange = DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE,
        key = "pull_refresh_spring",
    )

    val restorePillFraction by rememberDynamicSpringValue(
        targetValue = if (!showToolbar && !activeTab.isHome) 1f else 0f,
        stiffness = policy.toolbarSpring.stiffness,
        dampingRatio = policy.toolbarSpring.dampingRatio,
        minimumVisibleChange = DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE,
        key = "restore_pill_spring",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Scroll-aware elevated translucent M3 top toolbar
            if (toolbarSpringFraction > 0.01f || showToolbar) {
                val clampedFraction = toolbarSpringFraction.coerceIn(0f, 1f)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = policy.translucentChromeAlpha),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clipToBounds()
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val collapsedHeight = (placeable.height * clampedFraction).roundToInt()
                            layout(placeable.width, collapsedHeight) {
                                placeable.placeRelative(0, collapsedHeight - placeable.height)
                            }
                        }
                        .graphicsLayer {
                            alpha = clampedFraction
                            translationY = (toolbarSpringFraction - 1f) * 14f
                        },
                ) {
                    Column {
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val showInlineHome = !activeTab.isHome && (maxWidth >= 400.dp || !activeTab.canGoForward)
                            val homeFraction by rememberDynamicSpringValue(
                                targetValue = if (showInlineHome) 1f else 0f,
                                stiffness = policy.toolbarSpring.stiffness,
                                dampingRatio = policy.toolbarSpring.dampingRatio,
                                minimumVisibleChange = DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE,
                                key = "toolbar_home_spring",
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(ObscuraSizing.toolbarHeight)
                                    .padding(horizontal = ObscuraSpacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TactileIconButton(
                                    onClick = {
                                        if (!activeTab.isHome) {
                                            viewModel.goBack()
                                        }
                                    },
                                    icon = if (activeTab.isHome) Icons.Outlined.Home else Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = if (activeTab.isHome) "Start page" else stringResource(R.string.cd_back),
                                    enabled = !activeTab.isHome,
                                    modifier = Modifier.testTag("browser_back_button"),
                                )

                                if (forwardFraction > 0.02f) {
                                    val fwClamped = forwardFraction.coerceIn(0f, 1f)
                                    Box(
                                        modifier = Modifier
                                            .clipToBounds()
                                            .layout { measurable, constraints ->
                                                val placeable = measurable.measure(constraints)
                                                val width = (placeable.width * fwClamped).roundToInt()
                                                layout(width, placeable.height) {
                                                    placeable.placeRelative(0, 0)
                                                }
                                            }
                                            .graphicsLayer {
                                                alpha = fwClamped
                                                scaleX = 0.85f + 0.15f * forwardFraction
                                                scaleY = scaleX
                                            },
                                    ) {
                                        TactileIconButton(
                                            onClick = viewModel::goForward,
                                            icon = Icons.AutoMirrored.Outlined.ArrowForward,
                                            contentDescription = stringResource(R.string.cd_forward),
                                            modifier = Modifier.testTag("browser_forward_button"),
                                        )
                                    }
                                }

                                // Soft Elevated M3 Address / Search Capsule
                                val capsuleShape = RoundedCornerShape(ObscuraRadii.capsule)
                                val addressInteraction = remember { MutableInteractionSource() }
                                Surface(
                                    shape = capsuleShape,
                                    color = if (activeTab.isPrivate) {
                                        tabAccentContainer.copy(alpha = 0.45f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                    border = BorderStroke(
                                        ObscuraSizing.borderHairline,
                                        if (activeTab.isPrivate) tabAccent.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant,
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(ObscuraSizing.addressCapsuleHeight)
                                        .pressScale(addressInteraction, pressedScale = ObscuraMotionTokens.PRESSED_SCALE_CARD)
                                        .clip(capsuleShape)
                                        .semantics { contentDescription = "Address bar: $hostLabel" }
                                        .clickable(
                                            interactionSource = addressInteraction,
                                            indication = ripple(),
                                            role = Role.Button,
                                            onClick = { viewModel.setAddressFocused(true) },
                                        )
                                        .testTag("browser_address_capsule"),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(start = ObscuraSpacing.md, end = ObscuraSpacing.xxs),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = when {
                                                activeTab.isPrivate -> Icons.Outlined.VisibilityOff
                                                settings.stealthEnabled -> Icons.Outlined.Shield
                                                else -> Icons.Outlined.Lock
                                            },
                                            contentDescription = null,
                                            tint = if (activeTab.isPrivate || settings.stealthEnabled) {
                                                tabAccent
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.size(ObscuraSizing.iconSmall),
                                        )
                                        Spacer(Modifier.width(ObscuraSpacing.sm))
                                        Text(
                                            text = hostLabel,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (activeTab.isHome) {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (!activeTab.isHome && activeTab.blockedTrackers > 0) {
                                            Surface(
                                                shape = RoundedCornerShape(ObscuraRadii.pill),
                                                color = tabAccentContainer,
                                                modifier = Modifier.padding(end = ObscuraSpacing.xxs),
                                            ) {
                                                Text(
                                                    text = "${activeTab.blockedTrackers}",
                                                    fontFamily = JetBrainsMonoFamily,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = tabOnAccentContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                )
                                            }
                                        }
                                        if (!activeTab.isHome) {
                                            TactileIconButton(
                                                onClick = viewModel::reloadOrStop,
                                                icon = if (activeTab.isLoading) Icons.Outlined.Close else Icons.Outlined.Refresh,
                                                contentDescription = if (activeTab.isLoading) "Stop loading" else stringResource(R.string.cd_reload),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.testTag("browser_reload_button"),
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.width(ObscuraSpacing.xxs))

                                if (homeFraction > 0.02f) {
                                    val homeClamped = homeFraction.coerceIn(0f, 1f)
                                    Box(
                                        modifier = Modifier
                                            .clipToBounds()
                                            .layout { measurable, constraints ->
                                                val placeable = measurable.measure(constraints)
                                                val width = (placeable.width * homeClamped).roundToInt()
                                                layout(width, placeable.height) {
                                                    placeable.placeRelative(0, 0)
                                                }
                                            }
                                            .graphicsLayer {
                                                alpha = homeClamped
                                                scaleX = 0.85f + 0.15f * homeFraction
                                                scaleY = scaleX
                                            },
                                    ) {
                                        TactileIconButton(
                                            onClick = { viewModel.goHome() },
                                            icon = Icons.Outlined.Home,
                                            contentDescription = "Home",
                                            modifier = Modifier.testTag("browser_home_button"),
                                        )
                                    }
                                }

                                // Tab Switcher Badge Button (48dp touch target)
                                val tabBtnInteraction = remember { MutableInteractionSource() }
                                val tabCount = workspace.tabs.size
                                Box(
                                    modifier = Modifier
                                        .size(ObscuraSizing.minTouchTarget)
                                        .pressScale(tabBtnInteraction, pressedScale = ObscuraMotionTokens.PRESSED_SCALE_BUTTON)
                                        .clip(RoundedCornerShape(ObscuraRadii.sm))
                                        .semantics {
                                            contentDescription = "$tabCount open ${if (tabCount == 1) "tab" else "tabs"}, switch tabs"
                                        }
                                        .clickable(
                                            interactionSource = tabBtnInteraction,
                                            indication = ripple(bounded = false, radius = 22.dp),
                                            role = Role.Button,
                                            onClick = { viewModel.setTabSwitcherOpen(true) },
                                        )
                                        .testTag("browser_tabs_button"),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (activeTab.isPrivate) tabAccentContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                        border = BorderStroke(
                                            1.6.dp,
                                            if (activeTab.isPrivate) tabAccent else MaterialTheme.colorScheme.onSurface,
                                        ),
                                        modifier = Modifier.size(23.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = tabCount.coerceAtMost(99).toString(),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (activeTab.isPrivate) tabOnAccentContainer else MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                    }
                                }

                                TactileIconButton(
                                    onClick = { viewModel.setMenuOpen(true) },
                                    icon = Icons.Outlined.MoreVert,
                                    contentDescription = "Browser menu",
                                    modifier = Modifier.testTag("browser_menu_button"),
                                )
                            }
                        }

                        if (activeTab.isLoading) {
                            LinearProgressIndicator(
                                progress = { animatedProgress.coerceIn(0f, 1f) },
                                color = tabAccent,
                                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.5.dp),
                            )
                        } else {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }

            // Collapsible Find in Page Bar driven by SpringAnimation
            if (findBarFraction > 0.01f) {
                val findClamped = findBarFraction.coerceIn(0f, 1f)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clipToBounds()
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val height = (placeable.height * findClamped).roundToInt()
                            layout(placeable.width, height) {
                                placeable.placeRelative(0, height - placeable.height)
                            }
                        }
                        .graphicsLayer {
                            alpha = findClamped
                            translationY = (findBarFraction - 1f) * 10f
                        },
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = ObscuraSpacing.md, vertical = ObscuraSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(ObscuraRadii.capsule),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(ObscuraSizing.borderHairline, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.weight(1f),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = ObscuraSpacing.md, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(ObscuraSizing.iconSmall),
                                    )
                                    Spacer(Modifier.width(ObscuraSpacing.sm))
                                    Box(modifier = Modifier.weight(1f)) {
                                        if (workspace.findQuery.isEmpty()) {
                                            Text(
                                                text = "Find in page",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        BasicTextField(
                                            value = workspace.findQuery,
                                            onValueChange = viewModel::updateFindQuery,
                                            singleLine = true,
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                                color = MaterialTheme.colorScheme.onSurface,
                                            ),
                                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("find_in_page_input"),
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(ObscuraSpacing.sm))
                            Surface(
                                shape = RoundedCornerShape(ObscuraRadii.pill),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ) {
                                Text(
                                    text = "${workspace.findMatchIndex}/${workspace.findTotalMatches}",
                                    fontFamily = JetBrainsMonoFamily,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                            TactileIconButton(
                                onClick = { viewModel.findNext(false) },
                                icon = Icons.Outlined.KeyboardArrowUp,
                                contentDescription = "Previous match",
                            )
                            TactileIconButton(
                                onClick = { viewModel.findNext(true) },
                                icon = Icons.Outlined.KeyboardArrowDown,
                                contentDescription = "Next match",
                            )
                            TactileIconButton(
                                onClick = { viewModel.toggleFindInPage(false) },
                                icon = Icons.Outlined.Close,
                                contentDescription = "Close find",
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }

            // Primary Viewport: Home Start Page | Policy / Connection Card | Live WebView
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    activeTab.isHome -> {
                        BrowserHomeView(
                            shortcuts = shortcuts,
                            favicons = shortcutFavicons,
                            bookmarks = bookmarks,
                            recentHistory = history,
                            settings = settings,
                            isPrivateTab = activeTab.isPrivate,
                            trackerRuleCount = viewModel.trackerBlocklist.domainCount(),
                            onSearchClick = { viewModel.setAddressFocused(true) },
                            onOpenUrl = viewModel::navigateActiveTab,
                            onAddShortcut = viewModel::addShortcut,
                            onRemoveShortcut = viewModel::removeShortcut,
                            onOpenBookmarks = { viewModel.openDestination(BrowserDestination.BOOKMARKS) },
                            onOpenHistory = { viewModel.openDestination(BrowserDestination.HISTORY) },
                        )
                    }
                    activeTab.blockedMessage != null -> {
                        val isConnectionError = activeTab.blockedMessage.startsWith("Connection failed", ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(ObscuraSpacing.xxl),
                            contentAlignment = Alignment.Center,
                        ) {
                            val cardShape = MaterialTheme.shapes.large
                            Surface(
                                shape = cardShape,
                                color = MaterialTheme.colorScheme.surface,
                                border = obscuraRimBorder(),
                                modifier = Modifier
                                    .widthIn(max = 480.dp)
                                    .fillMaxWidth()
                                    .obscuraDepthShadow(ObscuraElevation.card, cardShape),
                            ) {
                                Column(
                                    modifier = Modifier.padding(ObscuraSpacing.xxl),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    DomainMonogramBadge(
                                        icon = if (isConnectionError) Icons.Outlined.WifiOff else Icons.Outlined.Security,
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.error,
                                        size = 56.dp,
                                    )
                                    Spacer(Modifier.height(ObscuraSpacing.md))
                                    Text(
                                        text = if (isConnectionError) "Page Unavailable" else "Navigation Blocked",
                                        style = MaterialTheme.typography.titleLarge,
                                        textAlign = TextAlign.Center,
                                    )
                                    Spacer(Modifier.height(ObscuraSpacing.sm))
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                        border = BorderStroke(ObscuraSizing.borderHairline, MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            text = activeTab.blockedMessage,
                                            fontFamily = JetBrainsMonoFamily,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(ObscuraSpacing.md),
                                        )
                                    }
                                    Spacer(Modifier.height(ObscuraSpacing.lg))
                                    Row(horizontalArrangement = Arrangement.spacedBy(ObscuraSpacing.sm)) {
                                        if (isConnectionError) {
                                            TactileOutlinedButton(
                                                onClick = { viewModel.retryActiveTab() },
                                                modifier = Modifier.testTag("error_retry_button"),
                                            ) {
                                                Text("Retry")
                                            }
                                        }
                                        TactileButton(
                                            onClick = { viewModel.goHome() },
                                            modifier = Modifier.testTag("blocked_return_home"),
                                        ) {
                                            Text("Return to Start Page")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        key(activeTab.id) {
                            var attachedWebView by remember { mutableStateOf<WebView?>(null) }
                            LaunchedEffect(activeTab.id, settings, attachedWebView) {
                                attachedWebView?.let { wv ->
                                    viewModel.applyWebViewSettings(wv, settings)
                                }
                            }
                            AndroidView(
                                factory = { ctx ->
                                    viewModel.getOrCreateWebView(activeTab.id, ctx).also { wv ->
                                        (wv.parent as? ViewGroup)?.removeView(wv)
                                        attachedWebView = wv
                                    }
                                },
                                update = { wv ->
                                    if (attachedWebView !== wv) attachedWebView = wv
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("obscura_webview"),
                            )
                        }

                        // Pull-to-refresh floating pill driven by SpringAnimation
                        if (animatedPull > 0.04f && !activeTab.isLoading) {
                            val readyToRelease = animatedPull >= 1f
                            val pillShape = RoundedCornerShape(ObscuraRadii.pill)
                            Surface(
                                shape = pillShape,
                                color = if (readyToRelease) {
                                    tabAccentContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                border = obscuraRimBorder(active = readyToRelease, activeColor = tabAccent),
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = ObscuraSpacing.md)
                                    .obscuraDepthShadow(ObscuraElevation.floating, pillShape)
                                    .graphicsLayer {
                                        translationY = (animatedPull * 28f).coerceAtMost(42f)
                                        alpha = (animatedPull * 1.4f).coerceIn(0f, 1f)
                                        val s = (0.9f + 0.1f * animatedPull.coerceAtMost(1.1f))
                                        scaleX = s
                                        scaleY = s
                                    },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Refresh,
                                        contentDescription = null,
                                        tint = if (readyToRelease) tabAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .graphicsLayer {
                                                rotationZ = animatedPull * 240f
                                            },
                                    )
                                    Text(
                                        text = if (readyToRelease) "Release to reload" else "Pull to reload",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (readyToRelease) tabOnAccentContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                // Floating contextual restore pill driven by SpringAnimation
                if (restorePillFraction > 0.02f) {
                    val pillClamped = restorePillFraction.coerceIn(0f, 1f)
                    val pillShape = RoundedCornerShape(ObscuraRadii.pill)
                    TactileCardSurface(
                        onClick = { viewModel.setToolbarVisible(true) },
                        shape = pillShape,
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = policy.translucentChromeAlpha),
                        baseElevation = ObscuraElevation.floating,
                        accessibilityLabel = "Show browser toolbar for $hostLabel",
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = ObscuraSpacing.sm)
                            .height(ObscuraSizing.minTouchTarget)
                            .graphicsLayer {
                                alpha = pillClamped
                                translationY = (1f - restorePillFraction) * -48f
                                val s = (0.9f + 0.1f * restorePillFraction).coerceAtLeast(0.5f)
                                scaleX = s
                                scaleY = s
                            }
                            .testTag("restore_toolbar_pill"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = when {
                                    activeTab.isPrivate -> Icons.Outlined.VisibilityOff
                                    settings.stealthEnabled -> Icons.Outlined.Shield
                                    else -> Icons.Outlined.Lock
                                },
                                contentDescription = null,
                                tint = tabAccent,
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                text = hostLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        // Focused Address Bar & Real-time Search Suggestions Overlay
        AddressFocusOverlay(
            visible = workspace.isAddressFocused,
            query = workspace.urlInput,
            suggestions = suggestions,
            searchEngineName = settings.searchEngine.label,
            onQueryChange = viewModel::updateUrlInput,
            onSubmit = viewModel::navigateActiveTab,
            onShareUrl = { url -> viewModel.shareText(activeTab.title.ifEmpty { url }, url) },
            onCopiedUrl = { viewModel.showStatusBanner("URL copied to clipboard") },
            onDismiss = { viewModel.setAddressFocused(false) },
        )

        // Multi-Tab Switcher Overlay
        TabSwitcherOverlay(
            visible = workspace.isTabSwitcherOpen,
            tabs = workspace.tabs,
            activeTabId = workspace.activeTabId,
            onSelectTab = viewModel::switchTab,
            onCloseTab = viewModel::closeTab,
            onNewTab = { isPrivate -> viewModel.openNewTab(isPrivate = isPrivate) },
            onCloseAll = viewModel::closeAllTabs,
            onDismiss = { viewModel.setTabSwitcherOpen(false) },
        )

        // Unified Browser Action Menu Bottom Sheet
        BrowserActionSheet(
            visible = workspace.isMenuOpen,
            settings = settings,
            isBookmarked = viewModel.isCurrentPageBookmarked(),
            isOnHome = activeTab.isHome,
            onDismiss = { viewModel.setMenuOpen(false) },
            onToggleStealth = {
                viewModel.updateSettings { it.copy(stealthEnabled = !it.stealthEnabled) }
            },
            onToggleTheme = {
                viewModel.updateSettings { it.copy(darkTheme = !it.darkTheme) }
            },
            onToggleDesktop = {
                viewModel.updateSettings { it.copy(desktopMode = !it.desktopMode) }
            },
            onGoHome = { viewModel.goHome() },
            onNewTab = { viewModel.openNewTab() },
            onNewPrivateTab = { viewModel.openNewTab(isPrivate = true) },
            onToggleBookmark = {
                viewModel.toggleBookmarkCurrentPage()
                viewModel.setMenuOpen(false)
            },
            onCopyUrl = {
                clipboard.setText(AnnotatedString(activeTab.url))
                viewModel.setMenuOpen(false)
                viewModel.showStatusBanner("URL copied to clipboard")
            },
            onSharePage = {
                viewModel.setMenuOpen(false)
                viewModel.shareText(activeTab.title, activeTab.url)
            },
            onScreenshot = viewModel::captureScreenshot,
            onExportPdf = { viewModel.exportPdf(context) },
            onFindInPage = { viewModel.toggleFindInPage(true) },
            onNavigateDestination = viewModel::openDestination,
        )

        // Captured Screenshot Dialog
        (workspace.overlayMode as? com.example.data.BrowserOverlayMode.ScreenshotPreview)?.let { preview ->
            val bmp = preview.bitmap
            Dialog(onDismissRequest = viewModel::dismissScreenshot) {
                val dialogShape = MaterialTheme.shapes.large
                Surface(
                    shape = dialogShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = obscuraRimBorder(),
                    modifier = Modifier.obscuraDepthShadow(ObscuraElevation.floating, dialogShape),
                ) {
                    Column(modifier = Modifier.padding(ObscuraSpacing.lg)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Captured Screenshot", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "${bmp.width} × ${bmp.height} px · $hostLabel",
                                    fontFamily = JetBrainsMonoFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            TactileIconButton(
                                onClick = viewModel::dismissScreenshot,
                                icon = Icons.Outlined.Close,
                                contentDescription = "Close",
                            )
                        }
                        Spacer(Modifier.height(ObscuraSpacing.sm))
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            border = BorderStroke(ObscuraSizing.borderHairline, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Captured page screenshot",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(320.dp)
                                    .clip(MaterialTheme.shapes.medium),
                            )
                        }
                        Spacer(Modifier.height(ObscuraSpacing.md))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TactileTextButton(onClick = viewModel::dismissScreenshot) {
                                Text("Dismiss")
                            }
                            Spacer(Modifier.width(ObscuraSpacing.sm))
                            TactileButton(
                                onClick = { viewModel.shareScreenshot(bmp) },
                                modifier = Modifier.testTag("share_screenshot_button"),
                            ) {
                                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(ObscuraSpacing.xs))
                                Text("Share PNG")
                            }
                        }
                    }
                }
            }
        }
    }
}
