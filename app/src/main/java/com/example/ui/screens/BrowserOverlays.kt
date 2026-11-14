package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPasteGo
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.dynamicanimation.animation.DynamicAnimation
import com.example.data.AddressSuggestion
import com.example.data.BrowserDestination
import com.example.data.BrowserTabState
import com.example.data.ObscuraSettings
import com.example.data.SuggestionKind
import com.example.motion.DynamicSpringController
import com.example.motion.rememberDynamicSpringValue
import com.example.ui.components.DomainMonogramBadge
import com.example.ui.components.ObscuraEmptyState
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
import kotlin.math.abs
import kotlin.math.sign

@Composable
fun AddressFocusOverlay(
    visible: Boolean,
    query: String,
    suggestions: List<AddressSuggestion>,
    searchEngineName: String,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onShareUrl: (String) -> Unit,
    onCopiedUrl: () -> Unit,
    onDismiss: () -> Unit,
) {
    val policy = LocalMotionPolicy.current
    val focusRequester = remember { FocusRequester() }
    val clipboard = LocalClipboardManager.current
    val clipText = remember(visible) {
        clipboard.getText()?.text?.trim()?.takeIf { it.isNotEmpty() && it.length < 300 }
    }

    AnimatedVisibility(
        visible = visible,
        enter = if (policy.enableRichTransitions) {
            fadeIn(policy.quickSpring()) + slideInVertically(policy.gentleSpring()) { -it / 6 }
        } else {
            fadeIn(policy.quickSpring())
        },
        exit = if (policy.enableRichTransitions) {
            fadeOut(policy.quickSpring()) + slideOutVertically(policy.quickSpring()) { -it / 6 }
        } else {
            fadeOut(policy.quickSpring())
        },
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .imePadding(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = policy.translucentChromeAlpha),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = ObscuraSpacing.xs, vertical = ObscuraSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TactileIconButton(
                                onClick = onDismiss,
                                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Cancel",
                                modifier = Modifier.testTag("address_overlay_back"),
                            )

                            val capsuleShape = RoundedCornerShape(ObscuraRadii.capsule)
                            Surface(
                                shape = capsuleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(
                                    ObscuraSizing.borderThin,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .obscuraDepthShadow(ObscuraElevation.subtle, capsuleShape),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(start = ObscuraSpacing.md, end = ObscuraSpacing.xxs),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(ObscuraSizing.iconSmall),
                                    )
                                    Spacer(Modifier.width(ObscuraSpacing.sm))
                                    Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.CenterStart,
                                    ) {
                                        if (query.isEmpty()) {
                                            Text(
                                                text = "Search $searchEngineName or type URL",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        BasicTextField(
                                            value = query,
                                            onValueChange = onQueryChange,
                                            singleLine = true,
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                                fontFamily = JetBrainsMonoFamily,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            ),
                                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Uri,
                                                imeAction = ImeAction.Go,
                                            ),
                                            keyboardActions = KeyboardActions(
                                                onGo = { if (query.isNotBlank()) onSubmit(query) },
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .focusRequester(focusRequester)
                                                .testTag("address_focus_input"),
                                        )
                                    }
                                    if (query.isNotEmpty()) {
                                        TactileIconButton(
                                            onClick = { onQueryChange("") },
                                            icon = Icons.Outlined.Close,
                                            contentDescription = "Clear",
                                            modifier = Modifier.testTag("address_overlay_clear"),
                                        )
                                    }
                                }
                            }

                            if (query.isNotBlank()) {
                                TactileIconButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(query))
                                        onCopiedUrl()
                                    },
                                    icon = Icons.Outlined.ContentCopy,
                                    contentDescription = "Copy URL",
                                    modifier = Modifier.testTag("address_overlay_copy"),
                                )
                                TactileIconButton(
                                    onClick = { onShareUrl(query) },
                                    icon = Icons.Outlined.Share,
                                    contentDescription = "Share URL",
                                    modifier = Modifier.testTag("address_overlay_share"),
                                )
                            } else {
                                Spacer(Modifier.width(ObscuraSpacing.sm))
                            }
                        }

                        if (clipText != null && clipText != query) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = ObscuraSpacing.md, vertical = ObscuraSpacing.xs),
                            ) {
                                TactileCardSurface(
                                    onClick = { onSubmit(clipText) },
                                    shape = RoundedCornerShape(ObscuraRadii.sm),
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                    baseElevation = ObscuraElevation.flat,
                                    accessibilityLabel = "Paste and go to $clipText",
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = ObscuraSpacing.md, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ContentPasteGo,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(ObscuraSpacing.sm))
                                        Text(
                                            text = "Paste & go: $clipText",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }

                if (suggestions.isEmpty()) {
                    ObscuraEmptyState(
                        icon = Icons.Outlined.Search,
                        title = "Navigate or Search",
                        description = "Type a website domain, full URL, or search query for $searchEngineName.",
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = ObscuraSizing.maxContentWidthExpanded),
                        contentPadding = PaddingValues(ObscuraSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(ObscuraSpacing.sm),
                    ) {
                        items(suggestions, key = { "${it.kind.name}_${it.targetUrl}" }) { item ->
                            SuggestionRow(item = item, onClick = { onSubmit(item.targetUrl) })
                        }
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            runCatching { focusRequester.requestFocus() }
        }
    }
}

@Composable
private fun SuggestionRow(
    item: AddressSuggestion,
    onClick: () -> Unit,
) {
    val icon = when (item.kind) {
        SuggestionKind.BOOKMARK -> Icons.Outlined.Star
        SuggestionKind.HISTORY -> Icons.Outlined.History
        SuggestionKind.SHORTCUT -> Icons.Outlined.Language
        SuggestionKind.DIRECT_URL -> Icons.Outlined.Public
        SuggestionKind.WEB_SEARCH -> Icons.Outlined.Search
    }
    val highlight = item.kind == SuggestionKind.DIRECT_URL ||
        item.kind == SuggestionKind.WEB_SEARCH ||
        item.kind == SuggestionKind.BOOKMARK

    TactileCardSurface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        containerColor = MaterialTheme.colorScheme.surface,
        baseElevation = ObscuraElevation.subtle,
        accessibilityLabel = "${item.title}, ${item.subtitle}",
        modifier = Modifier
            .fillMaxWidth()
            .testTag("suggestion_${item.kind.name.lowercase()}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ObscuraSpacing.md, vertical = ObscuraSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DomainMonogramBadge(
                icon = icon,
                containerColor = if (highlight) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                contentColor = if (highlight) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                size = 38.dp,
            )
            Spacer(Modifier.width(ObscuraSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = item.subtitle,
                    fontFamily = JetBrainsMonoFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun TabSwitcherOverlay(
    visible: Boolean,
    tabs: List<BrowserTabState>,
    activeTabId: String,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: (Boolean) -> Unit,
    onCloseAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val policy = LocalMotionPolicy.current
    val extColors = LocalObscuraColors.current
    var showCloseAllConfirm by remember { mutableStateOf(false) }

    val overlaySpring by rememberDynamicSpringValue(
        targetValue = if (visible) 1f else 0f,
        stiffness = policy.tabCardSpring.stiffness,
        dampingRatio = policy.tabCardSpring.dampingRatio,
        minimumVisibleChange = DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE,
        key = "tab_switcher_overlay_spring",
    )

    if (visible || overlaySpring > 0.02f) {
        val clampedOverlay = overlaySpring.coerceIn(0f, 1f)
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .graphicsLayer {
                    alpha = clampedOverlay
                    translationY = (1f - overlaySpring) * 72f
                },
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = policy.translucentChromeAlpha),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = ObscuraSpacing.lg, end = ObscuraSpacing.xs, top = ObscuraSpacing.sm, bottom = ObscuraSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(
                                    text = "${tabs.size} ${if (tabs.size == 1) "Tab" else "Tabs"}",
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                Text(
                                    text = "Swipe card horizontally to close",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TactileTextButton(
                                    onClick = {
                                        if (tabs.size > 1 || tabs.any { !it.isHome }) {
                                            showCloseAllConfirm = true
                                        } else {
                                            onCloseAll()
                                        }
                                    },
                                    modifier = Modifier.testTag("tabs_close_all"),
                                ) {
                                    Text("Close all")
                                }
                                TactileIconButton(
                                    onClick = onDismiss,
                                    icon = Icons.Outlined.Close,
                                    contentDescription = "Close tab switcher",
                                    modifier = Modifier.testTag("tabs_close_overlay"),
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }

                BoxWithConstraints(modifier = Modifier.weight(1f)) {
                    val columns = if (maxWidth >= 560.dp) 2 else 1
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(ObscuraSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(ObscuraSpacing.md),
                        horizontalArrangement = Arrangement.spacedBy(ObscuraSpacing.md),
                    ) {
                        itemsIndexed(tabs, key = { _, t -> t.id }) { index, tab ->
                            TabSwitcherCard(
                                tab = tab,
                                index = index,
                                isActive = tab.id == activeTabId,
                                overlayVisible = visible,
                                onSelect = { onSelectTab(tab.id) },
                                onClose = { onCloseTab(tab.id) },
                                modifier = if (policy.enableRichTransitions) {
                                    Modifier.animateItem(
                                        fadeInSpec = policy.quickSpring(),
                                        fadeOutSpec = policy.quickSpring(),
                                        placementSpec = policy.gentleSpring(),
                                    )
                                } else {
                                    Modifier
                                },
                            )
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .obscuraDepthShadow(ObscuraElevation.chrome, RoundedCornerShape(0.dp)),
                ) {
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = ObscuraSpacing.lg, vertical = ObscuraSpacing.md),
                            horizontalArrangement = Arrangement.spacedBy(ObscuraSpacing.md),
                        ) {
                            TactileOutlinedButton(
                                onClick = { onNewTab(true) },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = extColors.privateContainer.copy(alpha = 0.45f),
                                    contentColor = extColors.onPrivateContainer,
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("new_private_tab_button"),
                            ) {
                                Icon(
                                    Icons.Outlined.VisibilityOff,
                                    contentDescription = null,
                                    tint = extColors.privateAccent,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(ObscuraSpacing.xs))
                                Text("Private Tab")
                            }
                            TactileButton(
                                onClick = { onNewTab(false) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("new_tab_button"),
                            ) {
                                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(ObscuraSpacing.xs))
                                Text("New Tab")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCloseAllConfirm) {
        AlertDialog(
            onDismissRequest = { showCloseAllConfirm = false },
            shape = MaterialTheme.shapes.large,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Close all tabs?", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    "All ${tabs.size} open tabs will be closed and replaced with a fresh Start Page.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TactileButton(
                    onClick = {
                        showCloseAllConfirm = false
                        onCloseAll()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text("Close all")
                }
            },
            dismissButton = {
                TactileTextButton(onClick = { showCloseAllConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun TabSwitcherCard(
    tab: BrowserTabState,
    index: Int,
    isActive: Boolean,
    overlayVisible: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val policy = LocalMotionPolicy.current
    val extColors = LocalObscuraColors.current
    val interaction = remember { MutableInteractionSource() }
    var cardWidthPx by remember { mutableStateOf(600f) }

    val tabAccent = if (tab.isPrivate) extColors.privateAccent else MaterialTheme.colorScheme.primary
    val tabAccentContainer = if (tab.isPrivate) extColors.privateContainer else MaterialTheme.colorScheme.primaryContainer
    val tabOnAccentContainer = if (tab.isPrivate) extColors.onPrivateContainer else MaterialTheme.colorScheme.onPrimaryContainer

    var appeared by remember(tab.id) { mutableStateOf(false) }
    LaunchedEffect(overlayVisible) {
        if (overlayVisible) appeared = true
    }
    val staggerFactor = (1f - (index.coerceAtMost(6) * 0.05f)).coerceAtLeast(0.7f)
    val cardEntrance by rememberDynamicSpringValue(
        targetValue = if (appeared) 1f else 0f,
        stiffness = policy.tabCardSpring.stiffness * staggerFactor,
        dampingRatio = policy.tabCardSpring.dampingRatio,
        minimumVisibleChange = DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE,
        key = "tab_card_entrance_${tab.id}",
    )

    val swipeController = remember(tab.id) {
        DynamicSpringController(
            initialValue = 0f,
            minimumVisibleChange = DynamicAnimation.MIN_VISIBLE_CHANGE_PIXELS,
        )
    }
    DisposableEffect(swipeController) {
        onDispose { swipeController.dispose() }
    }
    LaunchedEffect(policy.tabCardSpring, swipeController) {
        swipeController.configure(policy.tabCardSpring)
    }

    val swipeOffset = swipeController.value
    val swipeAlpha = (1f - (abs(swipeOffset) / cardWidthPx.coerceAtLeast(1f)) * 0.85f).coerceIn(0.15f, 1f)
    val entranceClamped = cardEntrance.coerceIn(0f, 1f)
    val cardShape = MaterialTheme.shapes.large

    Surface(
        shape = cardShape,
        color = if (isActive) {
            if (tab.isPrivate) tabAccentContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerLowest
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = obscuraRimBorder(active = isActive, activeColor = tabAccent),
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { size ->
                if (size.width > 0) cardWidthPx = size.width.toFloat()
            }
            .obscuraDepthShadow(
                baseElevation = if (isActive) ObscuraElevation.floating else ObscuraElevation.card,
                shape = cardShape,
            )
            .graphicsLayer {
                translationX = swipeOffset
                translationY = (1f - cardEntrance) * 26f
                val s = (0.94f + 0.06f * cardEntrance).coerceAtLeast(0.5f)
                scaleX = s
                scaleY = s
                alpha = entranceClamped * swipeAlpha
            }
            .pointerInput(tab.id, policy.tabCardSpring, policy.reducedMotion) {
                val velocityTracker = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = { velocityTracker.resetTracking() },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        swipeController.snapTo(swipeController.value + dragAmount)
                    },
                    onDragEnd = {
                        val velocityX = velocityTracker.calculateVelocity().x
                        val currentX = swipeController.value
                        val thresholdPx = cardWidthPx * 0.36f
                        val flingDismiss = abs(velocityX) > 950f && sign(velocityX) == sign(currentX) && abs(currentX) > 36f
                        if (abs(currentX) >= thresholdPx || flingDismiss) {
                            onClose()
                        } else {
                            swipeController.configure(policy.tabCardSpring)
                            swipeController.animateTo(
                                targetValue = 0f,
                                initialVelocity = velocityX,
                                immediateSnap = policy.reducedMotion,
                            )
                        }
                    },
                    onDragCancel = {
                        swipeController.configure(policy.tabCardSpring)
                        swipeController.animateTo(0f, immediateSnap = policy.reducedMotion)
                    },
                )
            }
            .pressScale(interaction, pressedScale = ObscuraMotionTokens.PRESSED_SCALE_CARD)
            .clip(cardShape)
            .semantics {
                contentDescription = "${tab.title}, ${if (tab.isHome) "Start page" else tab.url}"
            }
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                role = Role.Button,
                onClick = onSelect,
            )
            .testTag("tab_card_${tab.id}"),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = ObscuraSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (tab.isLoading) {
                    Surface(
                        shape = RoundedCornerShape(ObscuraRadii.sm),
                        color = tabAccentContainer.copy(alpha = 0.72f),
                        modifier = Modifier.size(ObscuraSizing.badgeSize),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = tabAccent,
                            )
                        }
                    }
                } else {
                    DomainMonogramBadge(
                        icon = if (tab.isPrivate) Icons.Outlined.VisibilityOff else Icons.Outlined.Public,
                        containerColor = if (isActive || tab.isPrivate) {
                            tabAccentContainer.copy(alpha = 0.8f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        contentColor = if (isActive || tab.isPrivate) {
                            tabAccent
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Spacer(Modifier.width(ObscuraSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ObscuraSpacing.xs),
                    ) {
                        Text(
                            text = tab.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (isActive) {
                            StatusPill(
                                text = "ACTIVE",
                                containerColor = tabAccentContainer,
                                contentColor = tabOnAccentContainer,
                            )
                        }
                        if (tab.isPrivate) {
                            StatusPill(
                                text = "PRIVATE",
                                containerColor = extColors.privateContainer,
                                contentColor = extColors.onPrivateContainer,
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (tab.isHome) "about:home" else tab.url,
                        fontFamily = JetBrainsMonoFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (tab.blockedTrackers > 0) {
                    StatusPill(
                        text = "${tab.blockedTrackers}",
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                        contentColor = MaterialTheme.colorScheme.primary,
                        icon = Icons.Outlined.Shield,
                        modifier = Modifier.padding(end = ObscuraSpacing.xxs),
                    )
                }
                TactileIconButton(
                    onClick = onClose,
                    icon = Icons.Outlined.Close,
                    contentDescription = "Close ${tab.title}",
                    modifier = Modifier.testTag("close_tab_${tab.id}"),
                )
            }
            if (tab.isLoading) {
                LinearProgressIndicator(
                    progress = { (tab.progress / 100f).coerceIn(0.08f, 1f) },
                    color = tabAccent,
                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserActionSheet(
    visible: Boolean,
    settings: ObscuraSettings,
    isBookmarked: Boolean,
    isOnHome: Boolean,
    onDismiss: () -> Unit,
    onToggleStealth: () -> Unit,
    onToggleTheme: () -> Unit,
    onToggleDesktop: () -> Unit,
    onGoHome: () -> Unit,
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onToggleBookmark: () -> Unit,
    onCopyUrl: () -> Unit,
    onSharePage: () -> Unit,
    onScreenshot: () -> Unit,
    onExportPdf: () -> Unit,
    onFindInPage: () -> Unit,
    onNavigateDestination: (BrowserDestination) -> Unit,
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val extColors = LocalObscuraColors.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = ObscuraRadii.xl, topEnd = ObscuraRadii.xl),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.outline,
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ObscuraSpacing.lg)
                .padding(bottom = ObscuraSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(ObscuraSpacing.lg),
        ) {
            // Quick Toggles Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ObscuraSpacing.sm),
            ) {
                MenuToggleChip(
                    label = "Stealth",
                    icon = Icons.Outlined.Shield,
                    active = settings.stealthEnabled,
                    onClick = onToggleStealth,
                    testTag = "menu_toggle_stealth",
                    modifier = Modifier.weight(1f),
                )
                MenuToggleChip(
                    label = if (settings.darkTheme) "Night" else "Day",
                    icon = if (settings.darkTheme) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                    active = true,
                    onClick = onToggleTheme,
                    testTag = "menu_toggle_theme",
                    modifier = Modifier.weight(1f),
                )
                MenuToggleChip(
                    label = "Desktop",
                    icon = Icons.Outlined.Computer,
                    active = settings.desktopMode,
                    onClick = onToggleDesktop,
                    testTag = "menu_toggle_desktop",
                    modifier = Modifier.weight(1f),
                )
            }

            // Symmetrical 3 x 3 Quick Actions Grid
            Column(verticalArrangement = Arrangement.spacedBy(ObscuraSpacing.sm)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ObscuraSpacing.sm),
                ) {
                    MenuActionTile("Home", Icons.Outlined.Home, !isOnHome, false, onGoHome, "menu_home", Modifier.weight(1f))
                    MenuActionTile("New Tab", Icons.Outlined.Add, true, false, onNewTab, "menu_new_tab", Modifier.weight(1f))
                    MenuActionTile("Private", Icons.Outlined.VisibilityOff, true, false, onNewPrivateTab, "menu_private_tab", Modifier.weight(1f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ObscuraSpacing.sm),
                ) {
                    MenuActionTile(
                        if (isBookmarked) "Saved" else "Bookmark",
                        if (isBookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                        !isOnHome,
                        isBookmarked && !isOnHome,
                        onToggleBookmark,
                        "menu_bookmark",
                        Modifier.weight(1f),
                    )
                    MenuActionTile("Share", Icons.Outlined.Share, !isOnHome, false, onSharePage, "menu_share", Modifier.weight(1f))
                    MenuActionTile("Copy URL", Icons.Outlined.ContentCopy, !isOnHome, false, onCopyUrl, "menu_copy_url", Modifier.weight(1f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ObscuraSpacing.sm),
                ) {
                    MenuActionTile("Find", Icons.Outlined.FindInPage, !isOnHome, false, onFindInPage, "menu_find", Modifier.weight(1f))
                    MenuActionTile("Capture", Icons.Outlined.CameraAlt, !isOnHome, false, onScreenshot, "menu_screenshot", Modifier.weight(1f))
                    MenuActionTile("Export PDF", Icons.Outlined.PictureAsPdf, !isOnHome, false, onExportPdf, "menu_pdf", Modifier.weight(1f))
                }
            }

            // Library Destinations Card
            MenuDestinationGroup(title = "Library") {
                MenuListItem(
                    "Bookmarks",
                    "Saved pages & quick links",
                    Icons.Outlined.Star,
                    { onNavigateDestination(BrowserDestination.BOOKMARKS) },
                    "menu_dest_bookmarks",
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(horizontal = ObscuraSpacing.md),
                )
                MenuListItem(
                    "Downloads",
                    "Files downloaded from websites",
                    Icons.Outlined.Download,
                    { onNavigateDestination(BrowserDestination.DOWNLOADS) },
                    "menu_dest_downloads",
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(horizontal = ObscuraSpacing.md),
                )
                MenuListItem(
                    "History",
                    "Recent browsing timeline",
                    Icons.Outlined.History,
                    { onNavigateDestination(BrowserDestination.HISTORY) },
                    "menu_dest_history",
                )
            }

            // Tools & Engine Destinations Card
            MenuDestinationGroup(title = "Engine & Settings") {
                MenuListItem(
                    "DOM Extract & JS Eval",
                    "Markdown, Text, Links, Snapshot & --eval",
                    Icons.Outlined.Code,
                    { onNavigateDestination(BrowserDestination.EXTRACT) },
                    "menu_dest_extract",
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(horizontal = ObscuraSpacing.md),
                )
                MenuListItem(
                    "Batch Scraper",
                    "Parallel multi-URL extraction",
                    Icons.Outlined.Bolt,
                    { onNavigateDestination(BrowserDestination.SCRAPE) },
                    "menu_dest_scrape",
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(horizontal = ObscuraSpacing.md),
                )
                MenuListItem(
                    "Network & MCP Bridge",
                    "Tracker logs, console & local /mcp server",
                    Icons.Outlined.Hub,
                    { onNavigateDestination(BrowserDestination.NETWORK) },
                    "menu_dest_network",
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(horizontal = ObscuraSpacing.md),
                )
                MenuListItem(
                    if (extColors.isDark) "Settings & Stealth" else "Settings & Stealth",
                    "Day/Night theme, adaptive motion, search & SSRF",
                    Icons.Outlined.Settings,
                    { onNavigateDestination(BrowserDestination.SETTINGS) },
                    "menu_dest_settings",
                )
            }
        }
    }
}

@Composable
private fun MenuToggleChip(
    label: String,
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    TactileCardSurface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        baseElevation = if (active) ObscuraElevation.subtle else ObscuraElevation.flat,
        active = active,
        activeBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
        accessibilityLabel = "$label toggle, ${if (active) "on" else "off"}",
        modifier = modifier
            .height(ObscuraSizing.buttonHeight)
            .testTag(testTag),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = ObscuraSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(ObscuraSizing.iconSmall),
            )
            Spacer(Modifier.width(ObscuraSpacing.xs))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (active) {
                Spacer(Modifier.width(ObscuraSpacing.xs))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

@Composable
private fun MenuActionTile(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    highlighted: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.medium
    Surface(
        shape = shape,
        color = when {
            !enabled -> MaterialTheme.colorScheme.surfaceContainer
            highlighted -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surface
        },
        border = obscuraRimBorder(
            active = highlighted && enabled,
            activeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
        ),
        modifier = modifier
            .height(74.dp)
            .alpha(if (enabled) 1f else 0.42f)
            .then(
                if (enabled) Modifier.obscuraDepthShadow(ObscuraElevation.subtle, shape) else Modifier,
            )
            .pressScale(interaction, ObscuraMotionTokens.PRESSED_SCALE_TILE, enabled = enabled)
            .clip(shape)
            .semantics { contentDescription = label }
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = ripple(),
                role = Role.Button,
                onClick = onClick,
            )
            .testTag(testTag),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = ObscuraSpacing.xs, vertical = ObscuraSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(21.dp),
            )
            Spacer(Modifier.height(ObscuraSpacing.xs))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MenuDestinationGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ObscuraSpacing.xs)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = ObscuraSpacing.xxs),
        )
        val shape = MaterialTheme.shapes.large
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            border = obscuraRimBorder(),
            modifier = Modifier
                .fillMaxWidth()
                .obscuraDepthShadow(ObscuraElevation.card, shape),
        ) {
            Column {
                content()
            }
        }
    }
}

@Composable
private fun MenuListItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    testTag: String,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interaction, pressedScale = ObscuraMotionTokens.PRESSED_SCALE_CARD)
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = ObscuraSpacing.md, vertical = ObscuraSpacing.md)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DomainMonogramBadge(
            icon = icon,
            size = 38.dp,
        )
        Spacer(Modifier.width(ObscuraSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(ObscuraSizing.iconStandard),
        )
    }
}
