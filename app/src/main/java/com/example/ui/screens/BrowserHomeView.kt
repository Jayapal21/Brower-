package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.BookmarkItem
import com.example.data.HistoryItem
import com.example.data.ObscuraSettings
import com.example.data.WebsiteShortcut
import com.example.engine.FaviconRepository
import com.example.ui.components.DomainMonogramBadge
import com.example.ui.components.StatusPill
import com.example.ui.components.TactileButton
import com.example.ui.components.TactileCardSurface
import com.example.ui.components.TactileIconButton
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
import java.net.URI

@Composable
fun BrowserHomeView(
    shortcuts: List<WebsiteShortcut>,
    favicons: Map<String, Bitmap> = emptyMap(),
    bookmarks: List<BookmarkItem>,
    recentHistory: List<HistoryItem>,
    settings: ObscuraSettings,
    isPrivateTab: Boolean,
    trackerRuleCount: Int,
    onSearchClick: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onAddShortcut: (title: String, url: String) -> Unit,
    onRemoveShortcut: (String) -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    val extColors = LocalObscuraColors.current
    val accentColor = if (isPrivateTab) extColors.privateAccent else MaterialTheme.colorScheme.primary
    val accentContainer = if (isPrivateTab) extColors.privateContainer else MaterialTheme.colorScheme.primaryContainer
    val onAccentContainer = if (isPrivateTab) extColors.onPrivateContainer else MaterialTheme.colorScheme.onPrimaryContainer
    val auraPrimary = if (isPrivateTab) extColors.privateAccent.copy(alpha = 0.22f) else extColors.heroAuraPrimary
    val auraSecondary = if (isPrivateTab) extColors.privateAccent.copy(alpha = 0.10f) else extColors.heroAuraSecondary

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .drawBehind {
                val heroRadius = size.width.coerceAtLeast(360f) * 0.72f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(auraPrimary, Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 0.14f),
                        radius = heroRadius,
                    ),
                    radius = heroRadius,
                    center = Offset(size.width * 0.5f, size.height * 0.14f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(auraSecondary, Color.Transparent),
                        center = Offset(size.width * 0.85f, size.height * 0.28f),
                        radius = heroRadius * 0.65f,
                    ),
                    radius = heroRadius * 0.65f,
                    center = Offset(size.width * 0.85f, size.height * 0.28f),
                )
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        val columns = when {
            maxWidth > 640.dp -> 5
            maxWidth > 480.dp -> 4
            else -> 3
        }
        val compactHeight = maxHeight < 500.dp

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = ObscuraSizing.maxContentWidthCompact),
            contentPadding = PaddingValues(
                horizontal = ObscuraSpacing.xl,
                vertical = if (compactHeight) ObscuraSpacing.md else ObscuraSpacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(if (compactHeight) ObscuraSpacing.md else ObscuraSpacing.xl),
        ) {
            // Minimal Brand, Shield Status & Floating Hero Search Bar
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (!compactHeight) {
                        Spacer(Modifier.height(ObscuraSpacing.md))
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ObscuraSpacing.md),
                    ) {
                        val badgeShape = RoundedCornerShape(ObscuraRadii.sm)
                        Surface(
                            shape = badgeShape,
                            color = accentContainer,
                            border = BorderStroke(ObscuraSizing.borderHairline, accentColor.copy(alpha = 0.28f)),
                            modifier = Modifier
                                .size(44.dp)
                                .obscuraDepthShadow(ObscuraElevation.subtle, badgeShape),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPrivateTab) Icons.Outlined.VisibilityOff else Icons.Outlined.Shield,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(23.dp),
                                )
                            }
                        }
                        Column {
                            Text(
                                text = if (isPrivateTab) "Obscura Private" else "Obscura",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                text = if (isPrivateTab) {
                                    "Ephemeral session · Zero history saved"
                                } else {
                                    "Lightweight Stealth Browser"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(Modifier.height(ObscuraSpacing.md))

                    StatusPill(
                        text = buildString {
                            append(if (settings.stealthEnabled) "Stealth · $trackerRuleCount rules" else "Standard Mode")
                            if (!settings.allowPrivateNetwork) append(" · SSRF Guard")
                        },
                        containerColor = if (isPrivateTab) {
                            accentContainer.copy(alpha = 0.65f)
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                        },
                        contentColor = if (isPrivateTab) onAccentContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        icon = if (isPrivateTab) Icons.Outlined.VisibilityOff else Icons.Outlined.Lock,
                    )

                    Spacer(Modifier.height(if (compactHeight) ObscuraSpacing.md else ObscuraSpacing.xl))

                    // Elevated Floating Search Capsule
                    val searchShape = RoundedCornerShape(ObscuraRadii.capsule)
                    TactileCardSurface(
                        onClick = onSearchClick,
                        shape = searchShape,
                        containerColor = if (extColors.isDark) {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLowest
                        },
                        baseElevation = ObscuraElevation.floating,
                        active = isPrivateTab,
                        activeBorderColor = accentColor.copy(alpha = 0.45f),
                        accessibilityLabel = "Search ${settings.searchEngine.label} or enter URL",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ObscuraSizing.homeSearchHeight)
                            .testTag("home_search_bar"),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 18.dp, end = ObscuraSpacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(ObscuraSizing.iconStandard),
                            )
                            Spacer(Modifier.width(ObscuraSpacing.md))
                            Text(
                                text = "Search or enter URL",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Surface(
                                shape = RoundedCornerShape(ObscuraRadii.pill),
                                color = accentContainer.copy(alpha = 0.65f),
                            ) {
                                Text(
                                    text = settings.searchEngine.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = onAccentContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Website Shortcuts Grid
            item {
                Column(verticalArrangement = Arrangement.spacedBy(ObscuraSpacing.sm)) {
                    HomeSectionHeader(
                        title = "Quick Access",
                        actionLabel = "Add",
                        actionIcon = Icons.Outlined.Add,
                        actionTag = "home_add_shortcut_button",
                        onAction = { showAddDialog = true },
                    )

                    val rows = shortcuts.chunked(columns)
                    Column(verticalArrangement = Arrangement.spacedBy(ObscuraSpacing.sm)) {
                        rows.forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(ObscuraSpacing.sm),
                            ) {
                                rowItems.forEach { item ->
                                    val hostKey = remember(item.url) { FaviconRepository.hostKey(item.url) }
                                    ShortcutTile(
                                        shortcut = item,
                                        favicon = favicons[hostKey],
                                        accentColor = accentColor,
                                        accentContainer = accentContainer,
                                        onClick = { onOpenUrl(item.url) },
                                        onRemove = { onRemoveShortcut(item.id) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                repeat(columns - rowItems.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // Saved Bookmarks Shelf
            if (bookmarks.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(ObscuraSpacing.sm)) {
                        HomeSectionHeader(
                            title = "Bookmarks",
                            actionLabel = "See all",
                            actionTag = "home_view_all_bookmarks",
                            onAction = onOpenBookmarks,
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(ObscuraSpacing.sm),
                        ) {
                            items(bookmarks.take(8), key = { it.id }) { item ->
                                HomeBookmarkCard(
                                    bookmark = item,
                                    onClick = { onOpenUrl(item.url) },
                                )
                            }
                        }
                    }
                }
            }

            // Recent Browsing (hidden in private tab)
            if (!isPrivateTab && recentHistory.isNotEmpty()) {
                item {
                    HomeSectionHeader(
                        title = "Recent Browsing",
                        actionLabel = "See all",
                        actionTag = "home_view_all_history",
                        onAction = onOpenHistory,
                    )
                }

                items(recentHistory.take(5), key = { it.id }) { entry ->
                    RecentHistoryRow(
                        item = entry,
                        onClick = { onOpenUrl(entry.url) },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddShortcutDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { title, url ->
                onAddShortcut(title, url)
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun HomeSectionHeader(
    title: String,
    actionLabel: String,
    actionTag: String,
    onAction: () -> Unit,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TactileTextButton(
            onClick = onAction,
            modifier = Modifier.testTag(actionTag),
        ) {
            if (actionIcon != null) {
                Icon(actionIcon, contentDescription = null, modifier = Modifier.size(ObscuraSizing.iconSmall))
                Spacer(Modifier.width(ObscuraSpacing.xxs))
            }
            Text(actionLabel, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ShortcutTile(
    shortcut: WebsiteShortcut,
    favicon: Bitmap?,
    accentColor: Color,
    accentContainer: Color,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showRemove by remember(shortcut.id) { mutableStateOf(false) }
    val policy = LocalMotionPolicy.current
    val extColors = LocalObscuraColors.current

    TactileCardSurface(
        onClick = {
            if (showRemove) showRemove = false else onClick()
        },
        onLongClick = { showRemove = !showRemove },
        shape = MaterialTheme.shapes.medium,
        containerColor = MaterialTheme.colorScheme.surface,
        baseElevation = ObscuraElevation.card,
        pressedScale = ObscuraMotionTokens.PRESSED_SCALE_TILE,
        accessibilityLabel = "${shortcut.title} shortcut",
        modifier = modifier.testTag("shortcut_${shortcut.title.lowercase().replace(' ', '_')}"),
    ) {
        Box(modifier = Modifier.padding(vertical = 14.dp, horizontal = ObscuraSpacing.sm)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Crossfade(
                    targetState = favicon,
                    animationSpec = tween(durationMillis = if (policy.reducedMotion) 0 else 180),
                    label = "shortcut_favicon_${shortcut.id}",
                ) { bmp ->
                    if (bmp != null && !bmp.isRecycled) {
                        val imageBitmap = remember(bmp) { bmp.asImageBitmap() }
                        val iconShape = RoundedCornerShape(ObscuraRadii.sm)
                        Surface(
                            shape = iconShape,
                            color = extColors.faviconBackdrop,
                            border = BorderStroke(ObscuraSizing.borderHairline, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .size(42.dp)
                                .obscuraDepthShadow(ObscuraElevation.subtle, iconShape),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(7.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Image(
                                    bitmap = imageBitmap,
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    } else {
                        DomainMonogramBadge(
                            label = shortcut.badge,
                            containerColor = accentContainer.copy(alpha = 0.8f),
                            contentColor = accentColor,
                            size = 42.dp,
                        )
                    }
                }
                Spacer(Modifier.height(ObscuraSpacing.sm))
                Text(
                    text = shortcut.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AnimatedVisibility(
                visible = showRemove,
                enter = fadeIn(policy.quickSpring()) + scaleIn(policy.quickSpring()),
                exit = fadeOut(policy.quickSpring()) + scaleOut(policy.quickSpring()),
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                TactileIconButton(
                    onClick = onRemove,
                    icon = Icons.Outlined.Close,
                    contentDescription = "Remove ${shortcut.title}",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun HomeBookmarkCard(
    bookmark: BookmarkItem,
    onClick: () -> Unit,
) {
    val host = remember(bookmark.url) {
        runCatching { URI(bookmark.url).host?.removePrefix("www.") ?: bookmark.url }
            .getOrDefault(bookmark.url.removePrefix("https://").removePrefix("http://"))
    }
    TactileCardSurface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        containerColor = MaterialTheme.colorScheme.surface,
        baseElevation = ObscuraElevation.card,
        accessibilityLabel = "${bookmark.title}, $host",
        modifier = Modifier
            .width(196.dp)
            .testTag("home_bookmark_${bookmark.id}"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = ObscuraSpacing.md, vertical = ObscuraSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DomainMonogramBadge(
                icon = Icons.Outlined.Star,
                size = 34.dp,
            )
            Spacer(Modifier.width(ObscuraSpacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bookmark.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = host,
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
private fun RecentHistoryRow(
    item: HistoryItem,
    onClick: () -> Unit,
) {
    val host = remember(item.url) {
        runCatching { URI(item.url).host?.removePrefix("www.") ?: item.url }.getOrDefault(item.url)
    }
    TactileCardSurface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        containerColor = MaterialTheme.colorScheme.surface,
        baseElevation = ObscuraElevation.subtle,
        accessibilityLabel = item.title.ifEmpty { host },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ObscuraSpacing.md, vertical = ObscuraSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DomainMonogramBadge(
                icon = Icons.Outlined.History,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                size = 36.dp,
            )
            Spacer(Modifier.width(ObscuraSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title.ifEmpty { item.url },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = item.url,
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
private fun AddShortcutDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Add Quick Shortcut", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Label") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL (e.g. https://rust-lang.org)") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TactileButton(
                onClick = { if (url.isNotBlank()) onConfirm(title, url) },
                enabled = url.isNotBlank(),
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TactileTextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
