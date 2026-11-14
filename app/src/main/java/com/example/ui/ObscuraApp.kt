package com.example.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.AppTab
import com.example.ui.screens.BrowserScreen
import com.example.ui.screens.ExtractScreen
import com.example.ui.screens.NetworkScreen
import com.example.ui.screens.ScrapeScreen
import com.example.ui.screens.StealthScreen
import kotlinx.serialization.Serializable
import java.io.ByteArrayInputStream

@Serializable
data object BrowserRoute

@Serializable
data object ExtractRoute

@Serializable
data object ScrapeRoute

@Serializable
data object NetworkRoute

@Serializable
data object StealthRoute

private data class NavDestinationItem(
    val tab: AppTab,
    val route: Any,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
)

private val NAV_ITEMS = listOf(
    NavDestinationItem(AppTab.Browser, BrowserRoute, Icons.Filled.Language, Icons.Outlined.Language, "nav_browser"),
    NavDestinationItem(AppTab.Extract, ExtractRoute, Icons.Filled.Code, Icons.Outlined.Code, "nav_extract"),
    NavDestinationItem(AppTab.Scrape, ScrapeRoute, Icons.Filled.RocketLaunch, Icons.Outlined.RocketLaunch, "nav_scrape"),
    NavDestinationItem(AppTab.Network, NetworkRoute, Icons.Filled.Hub, Icons.Outlined.Hub, "nav_network"),
    NavDestinationItem(AppTab.Stealth, StealthRoute, Icons.Filled.Shield, Icons.Outlined.Shield, "nav_stealth"),
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ObscuraApp(
    viewModel: ObscuraViewModel,
    widthSizeClass: WindowWidthSizeClass,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val browserState by viewModel.browserState.collectAsStateWithLifecycle()
    val extractState by viewModel.extractState.collectAsStateWithLifecycle()
    val scrapeState by viewModel.scrapeState.collectAsStateWithLifecycle()
    val networkLogs by viewModel.networkLogs.collectAsStateWithLifecycle()
    val consoleLogs by viewModel.consoleLogs.collectAsStateWithLifecycle()
    val bridgeState by viewModel.bridgeState.collectAsStateWithLifecycle()
    val diagnosticResult by viewModel.diagnosticResult.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusBanner) {
        statusBanner?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusBanner()
        }
    }

    val appContext = LocalContext.current
    val sharedWebView = remember {
        createConfiguredWebView(appContext, viewModel).also {
            viewModel.attachWebView(it)
            viewModel.applyWebViewSettings(it, settings)
            if (!browserState.hasLoadedOnce) {
                viewModel.navigateTo(browserState.currentUrl)
            }
        }
    }

    DisposableEffect(sharedWebView) {
        onDispose {
            (sharedWebView.parent as? ViewGroup)?.removeView(sharedWebView)
            sharedWebView.destroy()
        }
    }

    val useRail = widthSizeClass != WindowWidthSizeClass.Compact

    fun navigateToRoute(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!useRail) {
                NavigationBar(
                    windowInsets = WindowInsets.navigationBars,
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    NAV_ITEMS.forEach { item ->
                        val selected = currentDestination?.hasRoute(item.route::class) == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigateToRoute(item.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.tab.label,
                                )
                            },
                            label = { Text(item.tab.label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag(item.testTag),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (useRail) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    NAV_ITEMS.forEach { item ->
                        val selected = currentDestination?.hasRoute(item.route::class) == true
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateToRoute(item.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.tab.label,
                                )
                            },
                            label = { Text(item.tab.label) },
                            modifier = Modifier.testTag(item.testTag),
                        )
                    }
                }
            }

            Box(
                modifier = Modifier.weight(1f).fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
                NavHost(
                    navController = navController,
                    startDestination = BrowserRoute,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    composable<BrowserRoute> {
                        BrowserScreen(
                            viewModel = viewModel,
                            state = browserState,
                            settings = settings,
                            isVisible = true,
                            obtainWebView = {
                                (sharedWebView.parent as? ViewGroup)?.removeView(sharedWebView)
                                sharedWebView
                            },
                        )
                    }
                    composable<ExtractRoute> {
                        ExtractScreen(
                            viewModel = viewModel,
                            state = extractState,
                            browserState = browserState,
                        )
                    }
                    composable<ScrapeRoute> {
                        ScrapeScreen(
                            viewModel = viewModel,
                            state = scrapeState,
                        )
                    }
                    composable<NetworkRoute> {
                        NetworkScreen(
                            viewModel = viewModel,
                            networkLogs = networkLogs,
                            consoleLogs = consoleLogs,
                            bridgeState = bridgeState,
                        )
                    }
                    composable<StealthRoute> {
                        StealthScreen(
                            viewModel = viewModel,
                            settings = settings,
                            diagnosticResult = diagnosticResult,
                        )
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createConfiguredWebView(
    context: Context,
    viewModel: ObscuraViewModel,
): WebView {
    return WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false

        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?,
            ): WebResourceResponse? {
                val reqUrl = request?.url?.toString() ?: return null
                val rType = if (request.isForMainFrame) "document" else "subresource"
                val blockOutcome = viewModel.shouldBlockSubresource(reqUrl, rType)
                if (blockOutcome != null) {
                    return WebResourceResponse(
                        "text/plain",
                        "UTF-8",
                        204,
                        "Blocked by Obscura",
                        emptyMap(),
                        ByteArrayInputStream(ByteArray(0)),
                    )
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (url != null) viewModel.onPageStarted(url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (view != null && url != null) {
                    viewModel.onPageFinished(view, url)
                }
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                viewModel.onPageProgress(newProgress)
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                if (title != null) viewModel.onPageTitle(title)
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                consoleMessage?.let {
                    viewModel.recordConsoleMessage(
                        level = it.messageLevel().name,
                        message = it.message(),
                        source = "${it.sourceId()}:${it.lineNumber()}",
                    )
                }
                return super.onConsoleMessage(consoleMessage)
            }
        }
    }
}
