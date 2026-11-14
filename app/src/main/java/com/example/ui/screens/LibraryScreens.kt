package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.BookmarkItem
import com.example.data.DownloadItem
import com.example.data.DownloadStatus
import com.example.data.HistoryItem
import com.example.ui.components.DomainMonogramBadge
import com.example.ui.components.ObscuraEmptyState
import com.example.ui.components.ObscuraSearchField
import com.example.ui.components.ObscuraSubScreenScaffold
import com.example.ui.components.StatusPill
import com.example.ui.components.TactileButton
import com.example.ui.components.TactileCardSurface
import com.example.ui.components.TactileIconButton
import com.example.ui.components.TactileTextButton
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalObscuraColors
import com.example.ui.theme.ObscuraElevation
import com.example.ui.theme.ObscuraSizing
import com.example.ui.theme.ObscuraSpacing
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BookmarksScreen(
    bookmarks: List<BookmarkItem>,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onAddBookmark: (String, String) -> Unit,
    onRemoveBookmark: (String) -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val filtered = remember(bookmarks, searchQuery) {
        if (searchQuery.isBlank()) bookmarks
        else bookmarks.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.url.contains(searchQuery, ignoreCase = true)
        }
    }

    ObscuraSubScreenScaffold(
        title = "Bookmarks",
        subtitle = "${bookmarks.size} saved",
        onBack = onBack,
        actions = {
            TactileIconButton(
                onClick = { showAdd = true },
                icon = Icons.Outlined.Add,
                contentDescription = "Add bookmark",
                modifier = Modifier.testTag("bookmarks_add_button"),
            )
        },
        pinnedHeader = if (bookmarks.isNotEmpty()) {
            {
                ObscuraSearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "Filter bookmarks",
                    modifier = Modifier.testTag("bookmarks_search_input"),
                )
            }
        } else null,
    ) { mod ->
        when {
            bookmarks.isEmpty() -> {
                ObscuraEmptyState(
                    icon = Icons.Outlined.Star,
                    title = "No Bookmarks Yet",
                    description = "Tap the Bookmark action in the browser menu or + above to save pages.",
                    modifier = mod,
                )
            }
            filtered.isEmpty() -> {
                ObscuraEmptyState(
                    icon = Icons.Outlined.Search,
                    title = "No Matching Bookmarks",
                    description = "No saved bookmarks match \"$searchQuery\".",
                    modifier = mod,
                )
            }
            else -> {
                LazyColumn(
                    modifier = mod.widthIn(max = ObscuraSizing.maxContentWidthExpanded),
                    contentPadding = PaddingValues(ObscuraSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(ObscuraSpacing.sm),
                ) {
                    items(filtered, key = { it.id }) { item ->
                        LibraryItemRow(
                            title = item.title,
                            subtitle = item.url,
                            leadingIcon = Icons.Outlined.Star,
                            onClick = { onOpenUrl(item.url) },
                            onDelete = { onRemoveBookmark(item.id) },
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddUrlDialog(
            title = "Add Bookmark",
            urlHint = "https://example.com",
            onDismiss = { showAdd = false },
            onConfirm = { label, url ->
                onAddBookmark(label, url)
                showAdd = false
            },
        )
    }
}

@Composable
fun HistoryScreen(
    history: List<HistoryItem>,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onRemoveItem: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    val dateFmt = remember { SimpleDateFormat("MMM d, HH:mm", Locale.US) }
    var searchQuery by remember { mutableStateOf("") }
    var showClearConfirm by remember { mutableStateOf(false) }

    val filtered = remember(history, searchQuery) {
        if (searchQuery.isBlank()) history
        else history.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.url.contains(searchQuery, ignoreCase = true)
        }
    }

    ObscuraSubScreenScaffold(
        title = "History",
        subtitle = "${history.size} visited pages",
        onBack = onBack,
        actions = {
            if (history.isNotEmpty()) {
                TactileIconButton(
                    onClick = { showClearConfirm = true },
                    icon = Icons.Outlined.DeleteSweep,
                    contentDescription = "Clear history",
                    modifier = Modifier.testTag("history_clear_button"),
                )
            }
        },
        pinnedHeader = if (history.isNotEmpty()) {
            {
                ObscuraSearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "Search browsing history",
                    modifier = Modifier.testTag("history_search_input"),
                )
            }
        } else null,
    ) { mod ->
        when {
            history.isEmpty() -> {
                ObscuraEmptyState(
                    icon = Icons.Outlined.History,
                    title = "No Browsing History",
                    description = "Pages visited in standard tabs appear here. Private tabs are never recorded.",
                    modifier = mod,
                )
            }
            filtered.isEmpty() -> {
                ObscuraEmptyState(
                    icon = Icons.Outlined.Search,
                    title = "No Matching History",
                    description = "No visited pages match \"$searchQuery\".",
                    modifier = mod,
                )
            }
            else -> {
                LazyColumn(
                    modifier = mod.widthIn(max = ObscuraSizing.maxContentWidthExpanded),
                    contentPadding = PaddingValues(ObscuraSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(ObscuraSpacing.sm),
                ) {
                    items(filtered, key = { it.id }) { item ->
                        LibraryItemRow(
                            title = item.title.ifEmpty { item.url },
                            subtitle = "${dateFmt.format(Date(item.visitedAt))} · ${item.url}",
                            leadingIcon = Icons.Outlined.History,
                            onClick = { onOpenUrl(item.url) },
                            onDelete = { onRemoveItem(item.id) },
                        )
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            shape = MaterialTheme.shapes.large,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Clear browsing history?", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    "All ${history.size} recorded visits will be permanently removed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TactileButton(
                    onClick = {
                        showClearConfirm = false
                        onClearHistory()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text("Clear all")
                }
            },
            dismissButton = {
                TactileTextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
fun DownloadsScreen(
    downloads: List<DownloadItem>,
    onBack: () -> Unit,
    onAddDownload: (String) -> Unit,
    onRemoveDownload: (Long) -> Unit,
    onOpenSystemDownloads: () -> Unit,
    onRefreshDownloads: () -> Unit = {},
) {
    var showNewDownload by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val extColors = LocalObscuraColors.current
    val hasActive = remember(downloads) { downloads.any { it.status == DownloadStatus.IN_PROGRESS } }

    LaunchedEffect(Unit) {
        onRefreshDownloads()
    }
    LaunchedEffect(hasActive) {
        while (hasActive) {
            delay(1500L)
            onRefreshDownloads()
        }
    }

    val filtered = remember(downloads, searchQuery) {
        if (searchQuery.isBlank()) downloads
        else downloads.filter {
            it.fileName.contains(searchQuery, ignoreCase = true) ||
                it.url.contains(searchQuery, ignoreCase = true)
        }
    }

    ObscuraSubScreenScaffold(
        title = "Downloads",
        subtitle = "${downloads.size} files",
        onBack = onBack,
        actions = {
            TactileIconButton(
                onClick = onOpenSystemDownloads,
                icon = Icons.Outlined.FolderOpen,
                contentDescription = "Open system downloads",
                modifier = Modifier.testTag("downloads_system_button"),
            )
            TactileIconButton(
                onClick = { showNewDownload = true },
                icon = Icons.Outlined.Add,
                contentDescription = "Download URL",
                modifier = Modifier.testTag("downloads_add_button"),
            )
        },
        pinnedHeader = if (downloads.isNotEmpty()) {
            {
                ObscuraSearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "Filter downloads",
                    modifier = Modifier.testTag("downloads_search_input"),
                )
            }
        } else null,
    ) { mod ->
        when {
            downloads.isEmpty() -> {
                ObscuraEmptyState(
                    icon = Icons.Outlined.Download,
                    title = "No Downloads",
                    description = "Files downloaded from websites or added via + appear here and save to your Downloads folder.",
                    modifier = mod,
                )
            }
            filtered.isEmpty() -> {
                ObscuraEmptyState(
                    icon = Icons.Outlined.Search,
                    title = "No Matching Downloads",
                    description = "No downloaded files match \"$searchQuery\".",
                    modifier = mod,
                )
            }
            else -> {
                LazyColumn(
                    modifier = mod.widthIn(max = ObscuraSizing.maxContentWidthExpanded),
                    contentPadding = PaddingValues(ObscuraSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(ObscuraSpacing.sm),
                ) {
                    items(filtered, key = { it.id }) { item ->
                        val (pillBg, pillFg, pillText) = when (item.status) {
                            DownloadStatus.COMPLETED -> Triple(
                                extColors.successContainer,
                                extColors.onSuccessContainer,
                                "SAVED",
                            )
                            DownloadStatus.IN_PROGRESS -> Triple(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.onPrimaryContainer,
                                "ACTIVE",
                            )
                            DownloadStatus.FAILED -> Triple(
                                MaterialTheme.colorScheme.errorContainer,
                                MaterialTheme.colorScheme.onErrorContainer,
                                "FAILED",
                            )
                        }

                        TactileCardSurface(
                            onClick = onOpenSystemDownloads,
                            shape = MaterialTheme.shapes.medium,
                            containerColor = MaterialTheme.colorScheme.surface,
                            baseElevation = ObscuraElevation.card,
                            accessibilityLabel = "${item.fileName}, $pillText",
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        start = ObscuraSpacing.md,
                                        top = ObscuraSpacing.md,
                                        bottom = ObscuraSpacing.md,
                                        end = ObscuraSpacing.xs,
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                DomainMonogramBadge(
                                    icon = Icons.Outlined.Download,
                                    size = 38.dp,
                                )
                                Spacer(Modifier.width(ObscuraSpacing.md))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.fileName,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    val meta = if (item.sizeBytes > 0L) {
                                        "${formatByteSize(item.sizeBytes)} · ${item.url}"
                                    } else {
                                        item.url
                                    }
                                    Text(
                                        text = meta,
                                        fontFamily = JetBrainsMonoFamily,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Spacer(Modifier.width(ObscuraSpacing.sm))
                                StatusPill(
                                    text = pillText,
                                    containerColor = pillBg,
                                    contentColor = pillFg,
                                )
                                TactileIconButton(
                                    onClick = { onRemoveDownload(item.id) },
                                    icon = Icons.Outlined.DeleteOutline,
                                    contentDescription = "Remove ${item.fileName}",
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewDownload) {
        AddUrlDialog(
            title = "Download File URL",
            urlHint = "https://example.com/file.pdf",
            showLabelField = false,
            onDismiss = { showNewDownload = false },
            onConfirm = { _, url ->
                onAddDownload(url)
                showNewDownload = false
            },
        )
    }
}

@Composable
private fun LibraryItemRow(
    title: String,
    subtitle: String,
    leadingIcon: ImageVector,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    TactileCardSurface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        containerColor = MaterialTheme.colorScheme.surface,
        baseElevation = ObscuraElevation.card,
        accessibilityLabel = title,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = ObscuraSpacing.md,
                    top = ObscuraSpacing.md,
                    bottom = ObscuraSpacing.md,
                    end = ObscuraSpacing.xs,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DomainMonogramBadge(
                icon = leadingIcon,
                size = 38.dp,
            )
            Spacer(Modifier.width(ObscuraSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontFamily = JetBrainsMonoFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TactileIconButton(
                onClick = onDelete,
                icon = Icons.Outlined.DeleteOutline,
                contentDescription = "Delete $title",
            )
        }
    }
}

@Composable
private fun AddUrlDialog(
    title: String,
    urlHint: String,
    showLabelField: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (showLabelField) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Title") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(urlHint) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TactileButton(
                onClick = { if (url.isNotBlank()) onConfirm(label, url) },
                enabled = url.isNotBlank(),
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TactileTextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private fun formatByteSize(bytes: Long): String = when {
    bytes >= 1_048_576L -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576f)
    bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024f)
    else -> "$bytes B"
}
