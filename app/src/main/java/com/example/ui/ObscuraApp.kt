package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.BrowserDestination
import com.example.data.BrowserOverlayMode
import com.example.motion.MotionTelemetry
import com.example.ui.screens.BookmarksScreen
import com.example.ui.screens.BrowserScreen
import com.example.ui.screens.DownloadsScreen
import com.example.ui.screens.ExtractScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.NetworkScreen
import com.example.ui.screens.ScrapeScreen
import com.example.ui.screens.StealthScreen
import com.example.ui.theme.LocalMotionPolicy
import com.example.ui.theme.LocalObscuraColors
import com.example.ui.theme.ObscuraRadii
import com.example.ui.theme.ObscuraSizing
import com.example.ui.theme.ObscuraSpacing

@Composable
fun ObscuraApp(
    viewModel: ObscuraViewModel,
    motionTelemetry: MotionTelemetry = MotionTelemetry(),
) {
    val policy = LocalMotionPolicy.current
    val extColors = LocalObscuraColors.current
    val destination by viewModel.destination.collectAsStateWithLifecycle()
    val workspace by viewModel.workspace.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val shortcuts by viewModel.shortcuts.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val suggestions by viewModel.addressSuggestions.collectAsStateWithLifecycle()
    val extractState by viewModel.extractState.collectAsStateWithLifecycle()
    val scrapeState by viewModel.scrapeState.collectAsStateWithLifecycle()
    val networkLogs by viewModel.networkLogs.collectAsStateWithLifecycle()
    val consoleLogs by viewModel.consoleLogs.collectAsStateWithLifecycle()
    val bridgeState by viewModel.bridgeState.collectAsStateWithLifecycle()
    val diagnosticResult by viewModel.diagnosticResult.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val pullRefreshProgress by viewModel.pullRefreshProgress.collectAsStateWithLifecycle()
    val shortcutFavicons by viewModel.shortcutFavicons.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusBanner) {
        statusBanner?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusBanner()
        }
    }

    // Unified BackHandler for overlays, sub-screens, and WebView back navigation
    val handleCustomBack = destination != BrowserDestination.BROWSER ||
        workspace.overlayMode != BrowserOverlayMode.None ||
        !workspace.activeTab.isHome

    BackHandler(enabled = handleCustomBack) {
        when (workspace.overlayMode) {
            is BrowserOverlayMode.ScreenshotPreview -> viewModel.dismissScreenshot()
            BrowserOverlayMode.AddressFocus -> viewModel.setAddressFocused(false)
            BrowserOverlayMode.TabSwitcher -> viewModel.setTabSwitcherOpen(false)
            BrowserOverlayMode.ActionSheet -> viewModel.setMenuOpen(false)
            BrowserOverlayMode.FindInPage -> viewModel.toggleFindInPage(false)
            BrowserOverlayMode.None -> {
                if (destination != BrowserDestination.BROWSER) {
                    viewModel.backToBrowser()
                } else {
                    viewModel.goBack()
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = destination,
            transitionSpec = {
                if (!policy.enableRichTransitions) {
                    fadeIn(policy.quickSpring()) togetherWith fadeOut(policy.quickSpring())
                } else if (targetState != BrowserDestination.BROWSER) {
                    (slideInHorizontally(policy.gentleSpring()) { it / 6 } +
                        fadeIn(policy.quickSpring())) togetherWith
                        fadeOut(policy.quickSpring())
                } else {
                    fadeIn(policy.quickSpring()) togetherWith
                        (slideOutHorizontally(policy.quickSpring()) { it / 6 } +
                            fadeOut(policy.quickSpring()))
                }
            },
            label = "browser_destination_transition",
        ) { currentDest ->
            when (currentDest) {
                BrowserDestination.BROWSER -> {
                    BrowserScreen(
                        viewModel = viewModel,
                        workspace = workspace,
                        settings = settings,
                        shortcuts = shortcuts,
                        bookmarks = bookmarks,
                        history = history,
                        suggestions = suggestions,
                        shortcutFavicons = shortcutFavicons,
                        pullRefreshProgress = pullRefreshProgress,
                    )
                }
                BrowserDestination.BOOKMARKS -> {
                    BookmarksScreen(
                        bookmarks = bookmarks,
                        onBack = viewModel::backToBrowser,
                        onOpenUrl = viewModel::navigateActiveTab,
                        onAddBookmark = viewModel::addBookmark,
                        onRemoveBookmark = viewModel::removeBookmark,
                    )
                }
                BrowserDestination.DOWNLOADS -> {
                    DownloadsScreen(
                        downloads = downloads,
                        onBack = viewModel::backToBrowser,
                        onAddDownload = viewModel::addManualDownload,
                        onRemoveDownload = viewModel::removeDownload,
                        onOpenSystemDownloads = viewModel::openSystemDownloads,
                        onRefreshDownloads = viewModel::refreshDownloadStatuses,
                    )
                }
                BrowserDestination.HISTORY -> {
                    HistoryScreen(
                        history = history,
                        onBack = viewModel::backToBrowser,
                        onOpenUrl = viewModel::navigateActiveTab,
                        onRemoveItem = viewModel::removeHistoryItem,
                        onClearHistory = viewModel::clearHistory,
                    )
                }
                BrowserDestination.EXTRACT -> {
                    ExtractScreen(
                        viewModel = viewModel,
                        state = extractState,
                        activeUrl = workspace.activeTab.url,
                        onBack = viewModel::backToBrowser,
                    )
                }
                BrowserDestination.SCRAPE -> {
                    ScrapeScreen(
                        viewModel = viewModel,
                        state = scrapeState,
                        onBack = viewModel::backToBrowser,
                    )
                }
                BrowserDestination.NETWORK -> {
                    NetworkScreen(
                        viewModel = viewModel,
                        networkLogs = networkLogs,
                        consoleLogs = consoleLogs,
                        bridgeState = bridgeState,
                        onBack = viewModel::backToBrowser,
                    )
                }
                BrowserDestination.SETTINGS -> {
                    StealthScreen(
                        viewModel = viewModel,
                        settings = settings,
                        diagnosticResult = diagnosticResult,
                        motionTelemetry = motionTelemetry,
                        onBack = viewModel::backToBrowser,
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(ObscuraSpacing.lg),
        ) { data ->
            Snackbar(
                snackbarData = data,
                shape = RoundedCornerShape(ObscuraRadii.md),
                containerColor = if (extColors.isDark) {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                } else {
                    MaterialTheme.colorScheme.onBackground
                },
                contentColor = if (extColors.isDark) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.surface
                },
                actionColor = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
