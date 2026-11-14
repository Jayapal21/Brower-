package com.example.ui

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AddressSuggestion
import com.example.data.BookmarkItem
import com.example.data.BrowserDestination
import com.example.data.BrowserOverlayMode
import com.example.data.BrowserTabState
import com.example.data.BrowserWorkspaceState
import com.example.data.ConsoleLogEntry
import com.example.data.DiagnosticCheckResult
import com.example.data.DownloadItem
import com.example.data.DownloadStatus
import com.example.data.DumpFormat
import com.example.data.HistoryItem
import com.example.data.InteractiveElementRef
import com.example.data.NetworkLogEntry
import com.example.data.NetworkOutcome
import com.example.data.ObscuraSettings
import com.example.data.ScrapeJobResult
import com.example.data.ScrapeMode
import com.example.data.ScrapeStatus
import com.example.data.ServerBridgeState
import com.example.data.SettingsRepository
import com.example.data.WebsiteShortcut
import com.example.engine.AddressSuggestionEngine
import com.example.engine.BrowserProfiles
import com.example.engine.ExtractionScripts
import com.example.engine.FaviconRepository
import com.example.engine.LocalMcpBridge
import com.example.engine.ObscuraHttpClient
import com.example.engine.RobotsCache
import com.example.engine.SsrfGuard
import com.example.engine.TrackerBlocklist
import com.example.ui.components.ObscuraWebView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class ExtractUiState(
    val selectedFormat: DumpFormat = DumpFormat.MARKDOWN,
    val extractedOutput: String = "",
    val isExtracting: Boolean = false,
    val jsExpression: String = "document.title",
    val jsResult: String = "",
    val isEvaluatingJs: Boolean = false,
    val snapshotRefs: List<InteractiveElementRef> = emptyList(),
)

data class ScrapeUiState(
    val urlsText: String = "https://example.com\nhttps://news.ycombinator.com\nhttps://quotes.toscrape.com",
    val concurrency: Int = 4,
    val mode: ScrapeMode = ScrapeMode.SUMMARY,
    val isRunning: Boolean = false,
    val completedCount: Int = 0,
    val totalCount: Int = 0,
    val results: List<ScrapeJobResult> = emptyList(),
)

class ObscuraViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    val trackerBlocklist = TrackerBlocklist(application)
    val robotsCache = RobotsCache()
    private val httpClient = ObscuraHttpClient(trackerBlocklist, robotsCache)
    private val faviconRepository = FaviconRepository(application, httpClient)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val logIdCounter = AtomicLong(1L)
    private val tabCounter = AtomicInteger(1)
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    val settings: StateFlow<ObscuraSettings> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, ObscuraSettings())

    val bookmarks: StateFlow<List<BookmarkItem>> = settingsRepo.bookmarksFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.DEFAULT_BOOKMARKS)

    val history: StateFlow<List<HistoryItem>> = settingsRepo.historyFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val shortcuts: StateFlow<List<WebsiteShortcut>> = settingsRepo.shortcutsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.DEFAULT_SHORTCUTS)

    val downloads: StateFlow<List<DownloadItem>> = settingsRepo.downloadsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _destination = MutableStateFlow(BrowserDestination.BROWSER)
    val destination: StateFlow<BrowserDestination> = _destination.asStateFlow()

    private val _workspace = MutableStateFlow(BrowserWorkspaceState())
    val workspace: StateFlow<BrowserWorkspaceState> = _workspace.asStateFlow()

    val addressSuggestions: StateFlow<List<AddressSuggestion>> = combine(
        _workspace,
        bookmarks,
        history,
        shortcuts,
        settings,
    ) { ws, bm, hist, sc, cfg ->
        if (!ws.isAddressFocused) emptyList()
        else AddressSuggestionEngine.buildSuggestions(ws.urlInput, bm, hist, sc, cfg.searchEngine)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _extractState = MutableStateFlow(ExtractUiState())
    val extractState: StateFlow<ExtractUiState> = _extractState.asStateFlow()

    private val _scrapeState = MutableStateFlow(ScrapeUiState())
    val scrapeState: StateFlow<ScrapeUiState> = _scrapeState.asStateFlow()

    private val _networkLogs = MutableStateFlow<List<NetworkLogEntry>>(emptyList())
    val networkLogs: StateFlow<List<NetworkLogEntry>> = _networkLogs.asStateFlow()

    private val _consoleLogs = MutableStateFlow<List<ConsoleLogEntry>>(emptyList())
    val consoleLogs: StateFlow<List<ConsoleLogEntry>> = _consoleLogs.asStateFlow()

    private val _bridgeState = MutableStateFlow(ServerBridgeState())
    val bridgeState: StateFlow<ServerBridgeState> = _bridgeState.asStateFlow()

    private val _diagnosticResult = MutableStateFlow<DiagnosticCheckResult?>(null)
    val diagnosticResult: StateFlow<DiagnosticCheckResult?> = _diagnosticResult.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _pullRefreshProgress = MutableStateFlow(0f)
    val pullRefreshProgress: StateFlow<Float> = _pullRefreshProgress.asStateFlow()

    private val _shortcutFavicons = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    val shortcutFavicons: StateFlow<Map<String, Bitmap>> = _shortcutFavicons.asStateFlow()

    private val tabWebViews = HashMap<String, WebView>()
    private val tabLoadStarts = HashMap<String, Long>()
    private val tabNavJobs = HashMap<String, Job>()
    private var scrapeJob: Job? = null
    private var faviconJob: Job? = null

    init {
        viewModelScope.launch {
            shortcuts
                .map { list -> list.map { it.url } }
                .distinctUntilChanged()
                .collect { urls -> prefetchShortcutFavicons(urls) }
        }
    }

    private fun prefetchShortcutFavicons(urls: List<String>) {
        faviconJob?.cancel()
        if (urls.isEmpty()) return
        val cfg = settings.value
        val semaphore = Semaphore(4)
        faviconJob = viewModelScope.launch {
            urls.map { url ->
                async {
                    val key = FaviconRepository.hostKey(url)
                    if (key.isEmpty()) return@async
                    val existing = faviconRepository.getCached(url)
                    if (existing != null && !existing.isRecycled) {
                        _shortcutFavicons.update { it + (key to existing) }
                        return@async
                    }
                    semaphore.withPermit {
                        val fetched = faviconRepository.getOrFetchFavicon(url, cfg)
                        if (fetched != null && !fetched.isRecycled) {
                            _shortcutFavicons.update { it + (key to fetched) }
                        }
                    }
                }
            }.awaitAll()
        }
    }

    private val mcpBridge = LocalMcpBridge(
        scope = viewModelScope,
        onNavigate = { url ->
            withContext(Dispatchers.Main) { navigateActiveTab(url) }
            "Navigated to $url"
        },
        onSnapshot = {
            withContext(Dispatchers.Main) {
                val tab = _workspace.value.activeTab
                val text = evalOnActiveWebView(ExtractionScripts.EXTRACT_TEXT_JS).take(2000)
                val refs = evalOnActiveWebView(ExtractionScripts.EXTRACT_SNAPSHOT_JS)
                "URL: ${tab.url}\nTitle: ${tab.title}\nRefs: $refs\nBody:\n$text"
            }
        },
        onEvaluate = { expr ->
            withContext(Dispatchers.Main) { evalOnActiveWebView(expr) }
        },
        onGetNetworkLogs = {
            _networkLogs.value.take(50).joinToString("\n") {
                "[${it.timestamp}] ${it.outcome.name} ${it.statusCode ?: "-"} ${it.url}"
            }
        },
        onGetConsoleLogs = {
            _consoleLogs.value.take(50).joinToString("\n") {
                "[${it.timestamp}] ${it.level}: ${it.message}"
            }
        },
        onStateChange = { running, port, reqCount, lastCall, err ->
            _bridgeState.value = ServerBridgeState(running, port, reqCount, lastCall, err)
        },
    )

    // --- Navigation & Contextual Overlays ---

    fun openDestination(dest: BrowserDestination) {
        tabWebViews[_workspace.value.activeTabId]?.clearMatches()
        _pullRefreshProgress.value = 0f
        _workspace.update {
            it.copy(
                overlayMode = BrowserOverlayMode.None,
                findQuery = "",
                findMatchIndex = 0,
                findTotalMatches = 0,
            )
        }
        _destination.value = dest
    }

    fun backToBrowser() {
        _destination.value = BrowserDestination.BROWSER
    }

    fun showStatusBanner(message: String) {
        _statusBanner.value = message
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    fun setMenuOpen(open: Boolean) {
        _workspace.update {
            val nextMode = when {
                open -> BrowserOverlayMode.ActionSheet
                it.overlayMode is BrowserOverlayMode.ActionSheet -> BrowserOverlayMode.None
                else -> it.overlayMode
            }
            it.copy(overlayMode = nextMode)
        }
    }

    fun setTabSwitcherOpen(open: Boolean) {
        if (open) {
            tabWebViews[_workspace.value.activeTabId]?.clearMatches()
            _pullRefreshProgress.value = 0f
        }
        _workspace.update {
            val nextMode = when {
                open -> BrowserOverlayMode.TabSwitcher
                it.overlayMode is BrowserOverlayMode.TabSwitcher -> BrowserOverlayMode.None
                else -> it.overlayMode
            }
            it.copy(
                overlayMode = nextMode,
                findQuery = if (open) "" else it.findQuery,
                findMatchIndex = if (open) 0 else it.findMatchIndex,
                findTotalMatches = if (open) 0 else it.findTotalMatches,
            )
        }
    }

    fun setAddressFocused(focused: Boolean) {
        val currentTab = _workspace.value.activeTab
        if (focused) {
            tabWebViews[currentTab.id]?.clearMatches()
        }
        _workspace.update { state ->
            val tab = state.activeTab
            val nextInput = if (tab.isHome) "" else tab.url
            val nextMode = when {
                focused -> BrowserOverlayMode.AddressFocus
                state.overlayMode is BrowserOverlayMode.AddressFocus -> BrowserOverlayMode.None
                else -> state.overlayMode
            }
            state.copy(
                overlayMode = nextMode,
                findQuery = if (focused) "" else state.findQuery,
                findMatchIndex = if (focused) 0 else state.findMatchIndex,
                findTotalMatches = if (focused) 0 else state.findTotalMatches,
                urlInput = nextInput,
                toolbarVisible = true,
            )
        }
    }

    fun updateUrlInput(value: String) {
        _workspace.update { it.copy(urlInput = value) }
    }

    fun setToolbarVisible(visible: Boolean) {
        _workspace.update { state ->
            if (state.toolbarVisible != visible && !state.isAddressFocused) {
                state.copy(toolbarVisible = visible)
            } else {
                state
            }
        }
    }

    // --- Multi-Tab WebView Lifecycle ---

    fun getOrCreateWebView(tabId: String, context: Context): WebView {
        val existing = tabWebViews[tabId]
        if (existing != null) {
            applyWebViewSettings(existing, settings.value)
            return existing
        }
        val isPrivate = _workspace.value.tabs.firstOrNull { it.id == tabId }?.isPrivate == true
        val created = ObscuraWebView(context.applicationContext).apply {
            onPullProgress = { fraction ->
                if (_workspace.value.activeTabId == tabId) {
                    _pullRefreshProgress.value = fraction
                }
            }
            onPullRefresh = {
                if (_workspace.value.activeTabId == tabId && !_workspace.value.activeTab.isLoading) {
                    reloadOrStop()
                }
            }
            this.settings.javaScriptEnabled = true
            this.settings.domStorageEnabled = true
            this.settings.databaseEnabled = !isPrivate
            @Suppress("DEPRECATION")
            this.settings.saveFormData = !isPrivate
            this.settings.loadWithOverviewMode = true
            this.settings.useWideViewPort = true
            this.settings.builtInZoomControls = true
            this.settings.displayZoomControls = false
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, !isPrivate)
            applyWebViewSettings(this, this@ObscuraViewModel.settings.value)

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): WebResourceResponse? {
                    val reqUrl = request?.url?.toString() ?: return null
                    val rType = if (request.isForMainFrame) "document" else "subresource"
                    val blockOutcome = shouldBlockSubresource(tabId, reqUrl, rType)
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
                    if (url != null) onTabPageStarted(tabId, url)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    if (view != null && url != null) onTabPageFinished(tabId, view, url)
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?,
                ) {
                    if (request?.isForMainFrame == true) {
                        val failedUrl = request.url?.toString() ?: _workspace.value.tabs.firstOrNull { it.id == tabId }?.url.orEmpty()
                        val desc = error?.description?.toString()?.takeIf { it.isNotBlank() } ?: "Unable to reach page"
                        recordNetworkLog("GET", failedUrl, "document", NetworkOutcome.ERROR, null, desc)
                        updateTab(tabId) {
                            it.copy(
                                isLoading = false,
                                progress = 0,
                                blockedMessage = "Connection failed: $desc",
                            )
                        }
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    updateTab(tabId) { it.copy(progress = newProgress) }
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    if (!title.isNullOrBlank()) {
                        updateTab(tabId) { it.copy(title = title) }
                    }
                }

                override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                    val pageUrl = view?.url ?: return
                    if (icon == null || icon.isRecycled) return
                    val isTabPrivate = _workspace.value.tabs.firstOrNull { it.id == tabId }?.isPrivate == true
                    val key = FaviconRepository.hostKey(pageUrl)
                    if (key.isEmpty()) return
                    val isShortcutHost = shortcuts.value.any { FaviconRepository.hostKey(it.url) == key }
                    if (!isTabPrivate || isShortcutHost) {
                        viewModelScope.launch {
                            val cached = faviconRepository.cacheLiveBitmap(pageUrl, icon, persistToDisk = !isTabPrivate)
                            if (cached != null && isShortcutHost) {
                                _shortcutFavicons.update { it + (cached.first to cached.second) }
                            }
                        }
                    }
                }

                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    consoleMessage?.let {
                        recordConsoleMessage(
                            it.messageLevel().name,
                            it.message(),
                            "${it.sourceId()}:${it.lineNumber()}",
                        )
                    }
                    return super.onConsoleMessage(consoleMessage)
                }
            }

            setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                enqueueDownload(url, userAgent, contentDisposition, mimetype, contentLength)
            }

            setFindListener { activeMatchOrdinal, numberOfMatches, _ ->
                _workspace.update {
                    it.copy(
                        findMatchIndex = if (numberOfMatches > 0) activeMatchOrdinal + 1 else 0,
                        findTotalMatches = numberOfMatches,
                    )
                }
            }

            setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                val delta = scrollY - oldScrollY
                if (scrollY <= 16) {
                    setToolbarVisible(true)
                } else if (delta > 28) {
                    setToolbarVisible(false)
                } else if (delta < -20) {
                    setToolbarVisible(true)
                }
            }
        }
        tabWebViews[tabId] = created
        return created
    }

    fun applyWebViewSettings(webView: WebView?, cfg: ObscuraSettings) {
        webView ?: return
        val ua = if (cfg.desktopMode) {
            BrowserProfiles.effectiveUserAgent(cfg.selectedProfileIndex, cfg.customUserAgent)
        } else if (cfg.customUserAgent.isNotBlank()) {
            cfg.customUserAgent.trim()
        } else {
            val profile = BrowserProfiles.getProfile(cfg.selectedProfileIndex)
            "Mozilla/5.0 (Linux; Android 15; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${profile.chromeMajor}.0.0.0 Mobile Safari/537.36"
        }
        if (webView.settings.userAgentString != ua) {
            webView.settings.userAgentString = ua
        }
        webView.settings.useWideViewPort = cfg.desktopMode || true
    }

    fun openNewTab(initialUrl: String? = null, isPrivate: Boolean = false) {
        tabWebViews[_workspace.value.activeTabId]?.clearMatches()
        _pullRefreshProgress.value = 0f
        val newId = "tab-${tabCounter.incrementAndGet()}"
        val tab = BrowserTabState(
            id = newId,
            title = if (isPrivate) "Private Tab" else "Start Page",
            isHome = initialUrl == null,
            isPrivate = isPrivate,
        )
        _workspace.update { state ->
            state.copy(
                tabs = state.tabs + tab,
                activeTabId = newId,
                overlayMode = BrowserOverlayMode.None,
                findQuery = "",
                findMatchIndex = 0,
                findTotalMatches = 0,
                urlInput = initialUrl ?: "",
                toolbarVisible = true,
            )
        }
        _destination.value = BrowserDestination.BROWSER
        if (initialUrl != null) {
            navigateTab(newId, initialUrl)
        }
    }

    fun switchTab(tabId: String) {
        tabWebViews[_workspace.value.activeTabId]?.clearMatches()
        _pullRefreshProgress.value = 0f
        _workspace.update { state ->
            val target = state.tabs.firstOrNull { it.id == tabId } ?: return@update state
            state.copy(
                activeTabId = tabId,
                overlayMode = BrowserOverlayMode.None,
                findQuery = "",
                findMatchIndex = 0,
                findTotalMatches = 0,
                urlInput = if (target.isHome) "" else target.url,
                toolbarVisible = true,
            )
        }
    }

    fun closeTab(tabId: String) {
        val wasPrivate = _workspace.value.tabs.firstOrNull { it.id == tabId }?.isPrivate == true
        tabNavJobs.remove(tabId)?.cancel()
        tabLoadStarts.remove(tabId)
        tabWebViews.remove(tabId)?.let { wv ->
            if (wasPrivate) {
                wv.clearCache(false)
                wv.clearFormData()
                wv.clearHistory()
            }
            (wv.parent as? android.view.ViewGroup)?.removeView(wv)
            wv.destroy()
        }
        _pullRefreshProgress.value = 0f
        _workspace.update { state ->
            val remaining = state.tabs.filterNot { it.id == tabId }
            if (remaining.isEmpty()) {
                val freshId = "tab-${tabCounter.incrementAndGet()}"
                state.copy(
                    tabs = listOf(BrowserTabState(id = freshId)),
                    activeTabId = freshId,
                    urlInput = "",
                    overlayMode = BrowserOverlayMode.None,
                    findQuery = "",
                    findMatchIndex = 0,
                    findTotalMatches = 0,
                    toolbarVisible = true,
                )
            } else {
                val nextActive = if (state.activeTabId == tabId) {
                    remaining.last()
                } else {
                    remaining.firstOrNull { it.id == state.activeTabId } ?: remaining.last()
                }
                val preservedOverlay = if (state.overlayMode is BrowserOverlayMode.TabSwitcher) {
                    BrowserOverlayMode.TabSwitcher
                } else {
                    BrowserOverlayMode.None
                }
                state.copy(
                    tabs = remaining,
                    activeTabId = nextActive.id,
                    overlayMode = preservedOverlay,
                    findQuery = "",
                    findMatchIndex = 0,
                    findTotalMatches = 0,
                    urlInput = if (nextActive.isHome) "" else nextActive.url,
                )
            }
        }
    }

    fun closeAllTabs() {
        tabNavJobs.values.forEach { it.cancel() }
        tabNavJobs.clear()
        tabLoadStarts.clear()
        val privateTabIds = _workspace.value.tabs.filter { it.isPrivate }.map { it.id }.toSet()
        tabWebViews.forEach { (id, wv) ->
            if (id in privateTabIds) {
                wv.clearCache(false)
                wv.clearFormData()
                wv.clearHistory()
            }
            (wv.parent as? android.view.ViewGroup)?.removeView(wv)
            wv.destroy()
        }
        tabWebViews.clear()
        _pullRefreshProgress.value = 0f
        val freshId = "tab-${tabCounter.incrementAndGet()}"
        _workspace.update { state ->
            state.copy(
                tabs = listOf(BrowserTabState(id = freshId)),
                activeTabId = freshId,
                urlInput = "",
                overlayMode = BrowserOverlayMode.None,
                findQuery = "",
                findMatchIndex = 0,
                findTotalMatches = 0,
                toolbarVisible = true,
            )
        }
    }

    fun navigateActiveTab(rawInput: String) {
        navigateTab(_workspace.value.activeTabId, rawInput)
    }

    fun retryActiveTab() {
        val tab = _workspace.value.activeTab
        if (!tab.isHome && tab.url != "about:home") {
            navigateTab(tab.id, tab.url)
        }
    }

    private fun navigateTab(tabId: String, rawInput: String) {
        tabNavJobs.remove(tabId)?.cancel()
        val cfg = settings.value
        val resolvedUrl = AddressSuggestionEngine.resolveInputToUrl(rawInput, cfg.searchEngine)
        tabWebViews[tabId]?.clearMatches()
        _pullRefreshProgress.value = 0f
        _workspace.update { state ->
            state.copy(
                overlayMode = BrowserOverlayMode.None,
                findQuery = "",
                findMatchIndex = 0,
                findTotalMatches = 0,
                urlInput = if (resolvedUrl == "about:home") "" else resolvedUrl,
                toolbarVisible = true,
            )
        }
        _destination.value = BrowserDestination.BROWSER

        if (resolvedUrl == "about:home") {
            updateTab(tabId) {
                it.copy(
                    url = "about:home",
                    title = if (it.isPrivate) "Private Tab" else "Start Page",
                    isHome = true,
                    isLoading = false,
                    blockedMessage = null,
                )
            }
            return
        }

        updateTab(tabId) {
            it.copy(
                isHome = false,
                blockedMessage = null,
                blockedTrackers = 0,
            )
        }

        tabNavJobs[tabId] = viewModelScope.launch {
            val ssrf = withContext(Dispatchers.IO) {
                SsrfGuard.validateUrl(resolvedUrl, cfg.allowPrivateNetwork)
            }
            if (_workspace.value.tabs.none { it.id == tabId }) return@launch
            if (!ssrf.allowed) {
                recordNetworkLog("GET", resolvedUrl, "document", NetworkOutcome.BLOCKED_SSRF, null, ssrf.reason)
                updateTab(tabId) { it.copy(isLoading = false, blockedMessage = ssrf.reason, url = resolvedUrl) }
                return@launch
            }

            val host = runCatching { URI(resolvedUrl).host.orEmpty() }.getOrDefault("")
            if (cfg.stealthEnabled) {
                val rule = trackerBlocklist.matchedRule(host)
                if (rule != null) {
                    val msg = "Blocked by stealth tracker blocklist ($rule)"
                    recordNetworkLog("GET", resolvedUrl, "document", NetworkOutcome.BLOCKED_TRACKER, null, msg)
                    updateTab(tabId) { it.copy(isLoading = false, blockedMessage = msg, url = resolvedUrl) }
                    return@launch
                }
            }

            val (robotsAllowed, robotsReason) = httpClient.ensureRobotsAllowed(resolvedUrl, cfg)
            if (_workspace.value.tabs.none { it.id == tabId }) return@launch
            if (!robotsAllowed) {
                recordNetworkLog("GET", resolvedUrl, "document", NetworkOutcome.BLOCKED_ROBOTS, null, robotsReason)
                updateTab(tabId) {
                    it.copy(isLoading = false, blockedMessage = robotsReason ?: "Disallowed by robots.txt", url = resolvedUrl)
                }
                return@launch
            }

            tabLoadStarts[tabId] = System.currentTimeMillis()
            updateTab(tabId) { it.copy(url = resolvedUrl, isLoading = true, progress = 10) }

            val wv = getOrCreateWebView(tabId, getApplication())
            applyWebViewSettings(wv, cfg)
            wv.loadUrl(resolvedUrl)
        }
    }

    private fun onTabPageStarted(tabId: String, url: String) {
        tabLoadStarts[tabId] = System.currentTimeMillis()
        (tabWebViews[tabId] as? ObscuraWebView)?.isPullEnabled = false
        updateTab(tabId) {
            it.copy(url = url, isHome = false, isLoading = true, blockedMessage = null)
        }
        _workspace.update { state ->
            if (state.activeTabId == tabId && !state.isAddressFocused) state.copy(urlInput = url) else state
        }
    }

    private fun onTabPageFinished(tabId: String, webView: WebView, url: String) {
        val elapsed = tabLoadStarts[tabId]?.let { System.currentTimeMillis() - it } ?: 0L
        (webView as? ObscuraWebView)?.isPullEnabled = true
        val cfg = settings.value
        if (cfg.stealthEnabled) {
            val profile = BrowserProfiles.getProfile(cfg.selectedProfileIndex)
            webView.evaluateJavascript(BrowserProfiles.buildStealthBootstrapJs(profile), null)
        }
        val title = webView.title?.takeIf { it.isNotBlank() } ?: url
        updateTab(tabId) {
            it.copy(
                url = url,
                title = title,
                isHome = false,
                isLoading = false,
                progress = 100,
                lastLoadMs = elapsed,
                canGoBack = webView.canGoBack(),
                canGoForward = webView.canGoForward(),
            )
        }
        _workspace.update { state ->
            if (state.activeTabId == tabId && !state.isAddressFocused) state.copy(urlInput = url) else state
        }
        recordNetworkLog("GET", url, "document", NetworkOutcome.ALLOWED, 200, "${elapsed}ms")
        val tab = _workspace.value.tabs.firstOrNull { it.id == tabId }
        if (tab != null && !tab.isPrivate) {
            recordHistoryVisit(title, url)
        }
    }

    private fun shouldBlockSubresource(tabId: String, url: String, resourceType: String): NetworkOutcome? {
        val cfg = settings.value
        val host = runCatching { URI(url).host.orEmpty() }.getOrDefault("")
        if (!cfg.allowPrivateNetwork && host.isNotEmpty() && SsrfGuard.isForbiddenHostLiteral(host)) {
            recordNetworkLog("GET", url, resourceType, NetworkOutcome.BLOCKED_SSRF, null, "SSRF literal host blocked")
            return NetworkOutcome.BLOCKED_SSRF
        }
        if (cfg.stealthEnabled && host.isNotEmpty()) {
            val rule = trackerBlocklist.matchedRule(host)
            if (rule != null) {
                recordNetworkLog("GET", url, resourceType, NetworkOutcome.BLOCKED_TRACKER, null, "Rule: $rule")
                updateTab(tabId) { it.copy(blockedTrackers = it.blockedTrackers + 1) }
                return NetworkOutcome.BLOCKED_TRACKER
            }
        }
        recordNetworkLog("GET", url, resourceType, NetworkOutcome.ALLOWED, null, null)
        return null
    }

    fun goBack(): Boolean {
        val tab = _workspace.value.activeTab
        val wv = tabWebViews[tab.id]
        return if (!tab.isHome && wv != null && wv.canGoBack()) {
            wv.goBack()
            true
        } else if (!tab.isHome) {
            goHome()
            true
        } else {
            false
        }
    }

    fun goForward() {
        val tab = _workspace.value.activeTab
        tabWebViews[tab.id]?.takeIf { it.canGoForward() }?.goForward()
    }

    fun goHome() {
        val tabId = _workspace.value.activeTabId
        tabNavJobs.remove(tabId)?.cancel()
        tabWebViews[tabId]?.clearMatches()
        (tabWebViews[tabId] as? ObscuraWebView)?.isPullEnabled = true
        _pullRefreshProgress.value = 0f
        updateTab(tabId) {
            it.copy(
                url = "about:home",
                title = if (it.isPrivate) "Private Tab" else "Start Page",
                isHome = true,
                isLoading = false,
                blockedMessage = null,
            )
        }
        _workspace.update {
            it.copy(
                urlInput = "",
                overlayMode = BrowserOverlayMode.None,
                findQuery = "",
                findMatchIndex = 0,
                findTotalMatches = 0,
                toolbarVisible = true,
            )
        }
    }

    fun reloadOrStop() {
        val tab = _workspace.value.activeTab
        if (tab.isHome) return
        val wv = tabWebViews[tab.id] ?: return
        if (tab.isLoading) {
            wv.stopLoading()
            (wv as? ObscuraWebView)?.isPullEnabled = true
            updateTab(tab.id) { it.copy(isLoading = false) }
        } else {
            tabLoadStarts[tab.id] = System.currentTimeMillis()
            (wv as? ObscuraWebView)?.isPullEnabled = false
            wv.reload()
        }
    }

    // --- Find in Page ---

    fun toggleFindInPage(open: Boolean) {
        val wv = tabWebViews[_workspace.value.activeTabId]
        if (!open) {
            wv?.clearMatches()
        }
        _workspace.update {
            val nextMode = when {
                open -> BrowserOverlayMode.FindInPage
                it.overlayMode is BrowserOverlayMode.FindInPage -> BrowserOverlayMode.None
                else -> it.overlayMode
            }
            it.copy(
                overlayMode = nextMode,
                findQuery = if (open) it.findQuery else "",
                findMatchIndex = 0,
                findTotalMatches = 0,
            )
        }
    }

    fun updateFindQuery(query: String) {
        _workspace.update { it.copy(findQuery = query) }
        val wv = tabWebViews[_workspace.value.activeTabId] ?: return
        if (query.isEmpty()) {
            wv.clearMatches()
            _workspace.update { it.copy(findMatchIndex = 0, findTotalMatches = 0) }
        } else {
            wv.findAllAsync(query)
        }
    }

    fun findNext(forward: Boolean) {
        tabWebViews[_workspace.value.activeTabId]?.findNext(forward)
    }

    // --- Screenshots & PDF ---

    fun captureScreenshot() {
        val tab = _workspace.value.activeTab
        val webView = tabWebViews[tab.id]
        if (tab.isHome || webView == null || webView.width <= 0 || webView.height <= 0) {
            _statusBanner.value = "Open a webpage first to capture a screenshot."
            return
        }
        val bitmap = Bitmap.createBitmap(webView.width, webView.height, Bitmap.Config.ARGB_8888)
        webView.draw(Canvas(bitmap))
        _workspace.update { it.copy(overlayMode = BrowserOverlayMode.ScreenshotPreview(bitmap)) }
    }

    fun dismissScreenshot() {
        _workspace.update {
            if (it.overlayMode is BrowserOverlayMode.ScreenshotPreview) {
                it.copy(overlayMode = BrowserOverlayMode.None)
            } else {
                it
            }
        }
    }

    fun shareScreenshot(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, "obscura_capture_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(Intent.createChooser(intent, "Share Screenshot").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    fun exportPdf(activityContext: Context) {
        val tab = _workspace.value.activeTab
        val webView = tabWebViews[tab.id]
        if (tab.isHome || webView == null) {
            _statusBanner.value = "Open a webpage first to export PDF."
            return
        }
        _workspace.update {
            if (it.overlayMode is BrowserOverlayMode.ActionSheet) it.copy(overlayMode = BrowserOverlayMode.None) else it
        }
        val printManager = activityContext.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val jobName = "Obscura_${tab.title.take(24)}"
        printManager.print(jobName, webView.createPrintDocumentAdapter(jobName), PrintAttributes.Builder().build())
    }

    // --- Bookmarks, Shortcuts, History, Downloads ---

    fun isCurrentPageBookmarked(): Boolean {
        val url = _workspace.value.activeTab.url
        return bookmarks.value.any { it.url == url }
    }

    fun toggleBookmarkCurrentPage() {
        val tab = _workspace.value.activeTab
        if (tab.isHome) {
            _statusBanner.value = "Open a webpage to bookmark it."
            return
        }
        val current = bookmarks.value.toMutableList()
        val existingIdx = current.indexOfFirst { it.url == tab.url }
        if (existingIdx >= 0) {
            current.removeAt(existingIdx)
            _statusBanner.value = "Bookmark removed"
        } else {
            current.add(0, BookmarkItem(UUID.randomUUID().toString(), tab.title.ifEmpty { tab.url }, tab.url))
            _statusBanner.value = "Bookmarked ${tab.title.take(28)}"
        }
        viewModelScope.launch { settingsRepo.saveBookmarks(current) }
    }

    fun addBookmark(title: String, url: String) {
        val normalized = httpClient.normalizeUrl(url)
        val next = listOf(BookmarkItem(UUID.randomUUID().toString(), title.ifBlank { normalized }, normalized)) +
            bookmarks.value.filterNot { it.url == normalized }
        viewModelScope.launch { settingsRepo.saveBookmarks(next) }
    }

    fun removeBookmark(id: String) {
        val next = bookmarks.value.filterNot { it.id == id }
        viewModelScope.launch { settingsRepo.saveBookmarks(next) }
    }

    fun addShortcut(title: String, url: String) {
        val normalized = httpClient.normalizeUrl(url)
        val label = title.ifBlank { normalized.removePrefix("https://").removePrefix("http://").substringBefore('/') }
        val badge = label.take(2).uppercase()
        val next = shortcuts.value + WebsiteShortcut(UUID.randomUUID().toString(), label, normalized, badge)
        viewModelScope.launch { settingsRepo.saveShortcuts(next) }
    }

    fun removeShortcut(id: String) {
        val next = shortcuts.value.filterNot { it.id == id }
        viewModelScope.launch { settingsRepo.saveShortcuts(next) }
    }

    private fun recordHistoryVisit(title: String, url: String) {
        if (url == "about:home") return
        val next = listOf(HistoryItem(UUID.randomUUID().toString(), title, url)) +
            history.value.filterNot { it.url == url }.take(140)
        viewModelScope.launch { settingsRepo.saveHistory(next) }
    }

    fun removeHistoryItem(id: String) {
        val next = history.value.filterNot { it.id == id }
        viewModelScope.launch { settingsRepo.saveHistory(next) }
    }

    fun clearHistory() {
        viewModelScope.launch { settingsRepo.saveHistory(emptyList()) }
        _statusBanner.value = "Browsing history cleared."
    }

    fun enqueueDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimetype: String?,
        contentLength: Long,
    ) {
        val ctx = getApplication<Application>()
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype).ifBlank { "download.bin" }
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        var downloadId = System.currentTimeMillis()
        var status = DownloadStatus.IN_PROGRESS

        if (dm != null && (url.startsWith("http://") || url.startsWith("https://"))) {
            runCatching {
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setTitle(fileName)
                    setDescription("Obscura Download")
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    if (!userAgent.isNullOrBlank()) addRequestHeader("User-Agent", userAgent)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                    } else {
                        setDestinationInExternalFilesDir(ctx, Environment.DIRECTORY_DOWNLOADS, fileName)
                    }
                }
                downloadId = dm.enqueue(request)
                status = DownloadStatus.IN_PROGRESS
            }.onFailure {
                status = DownloadStatus.FAILED
            }
        } else {
            status = DownloadStatus.FAILED
        }

        val item = DownloadItem(
            id = downloadId,
            fileName = fileName,
            url = url,
            mimeType = mimetype ?: "application/octet-stream",
            sizeBytes = contentLength.coerceAtLeast(0L),
            status = status,
        )
        val next = listOf(item) + downloads.value
        viewModelScope.launch { settingsRepo.saveDownloads(next) }
        _statusBanner.value = if (status != DownloadStatus.FAILED) "Downloading $fileName" else "Download failed for $fileName"
    }

    fun refreshDownloadStatuses() {
        val current = downloads.value
        val activeIds = current.filter { it.status == DownloadStatus.IN_PROGRESS }.map { it.id }
        if (activeIds.isEmpty()) return
        val ctx = getApplication<Application>()
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val statusById = HashMap<Long, DownloadStatus>()
            val query = DownloadManager.Query().setFilterById(*activeIds.toLongArray())
            runCatching {
                dm.query(query)?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
                    val stIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    if (idIdx >= 0 && stIdx >= 0) {
                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(idIdx)
                            val mapped = when (cursor.getInt(stIdx)) {
                                DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.COMPLETED
                                DownloadManager.STATUS_FAILED -> DownloadStatus.FAILED
                                else -> DownloadStatus.IN_PROGRESS
                            }
                            statusById[id] = mapped
                        }
                    }
                }
            }
            if (statusById.isNotEmpty()) {
                val updated = current.map { item ->
                    val fresh = statusById[item.id]
                    if (fresh != null && fresh != item.status) item.copy(status = fresh) else item
                }
                if (updated != current) {
                    settingsRepo.saveDownloads(updated)
                }
            }
        }
    }

    fun addManualDownload(url: String) {
        val normalized = httpClient.normalizeUrl(url)
        enqueueDownload(
            url = normalized,
            userAgent = BrowserProfiles.effectiveUserAgent(settings.value.selectedProfileIndex, settings.value.customUserAgent),
            contentDisposition = null,
            mimetype = null,
            contentLength = 0L,
        )
    }

    fun removeDownload(id: Long) {
        val next = downloads.value.filterNot { it.id == id }
        viewModelScope.launch { settingsRepo.saveDownloads(next) }
    }

    fun openSystemDownloads() {
        val ctx = getApplication<Application>()
        runCatching {
            val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        }.onFailure {
            _statusBanner.value = "System downloads viewer unavailable."
        }
    }

    // --- Extract (--dump / --eval) ---

    fun selectDumpFormat(format: DumpFormat) {
        _extractState.value = _extractState.value.copy(selectedFormat = format)
        runExtraction(format)
    }

    fun runExtraction(format: DumpFormat = _extractState.value.selectedFormat) {
        _extractState.value = _extractState.value.copy(isExtracting = true)
        viewModelScope.launch {
            val currentUrl = _workspace.value.activeTab.url
            val result = when (format) {
                DumpFormat.MARKDOWN -> evalOnActiveWebView(ExtractionScripts.HTML_TO_MARKDOWN_JS)
                DumpFormat.TEXT -> evalOnActiveWebView(ExtractionScripts.EXTRACT_TEXT_JS)
                DumpFormat.LINKS -> evalOnActiveWebView(ExtractionScripts.EXTRACT_LINKS_JS)
                DumpFormat.HTML -> evalOnActiveWebView(ExtractionScripts.EXTRACT_HTML_JS)
                DumpFormat.ASSETS -> evalOnActiveWebView(ExtractionScripts.EXTRACT_ASSETS_JS)
                DumpFormat.COOKIES -> {
                    val cookieStr = CookieManager.getInstance().getCookie(currentUrl).orEmpty()
                    if (cookieStr.isBlank()) "[]" else {
                        val pairs = cookieStr.split(";").map { it.trim() }.filter { it.contains("=") }
                        buildJsonArray {
                            pairs.forEach { p ->
                                val idx = p.indexOf('=')
                                add(buildJsonObject {
                                    put("name", p.substring(0, idx).trim())
                                    put("value", p.substring(idx + 1).trim())
                                    put("url", currentUrl)
                                })
                            }
                        }.toString()
                    }
                }
                DumpFormat.SNAPSHOT -> {
                    val rawJson = evalOnActiveWebView(ExtractionScripts.EXTRACT_SNAPSHOT_JS)
                    val parsed = runCatching {
                        json.decodeFromString<List<InteractiveElementRef>>(rawJson)
                    }.getOrDefault(emptyList())
                    _extractState.value = _extractState.value.copy(snapshotRefs = parsed)
                    rawJson
                }
                DumpFormat.ORIGINAL -> {
                    if (currentUrl == "about:home") "(open a webpage first)"
                    else {
                        val resp = httpClient.fetchRaw(currentUrl, settings.value)
                        if (resp.error != null) "Error: ${resp.error}" else resp.body
                    }
                }
            }
            _extractState.value = _extractState.value.copy(
                extractedOutput = result.ifBlank { "(empty output — open a webpage first)" },
                isExtracting = false,
            )
        }
    }

    fun updateJsExpression(expr: String) {
        _extractState.value = _extractState.value.copy(jsExpression = expr)
    }

    fun evaluateJs(expr: String = _extractState.value.jsExpression) {
        if (expr.isBlank()) return
        _extractState.value = _extractState.value.copy(isEvaluatingJs = true, jsExpression = expr)
        viewModelScope.launch {
            val wrapped = """
                (function() {
                    try {
                        var __r = ($expr);
                        return JSON.stringify(__r === undefined ? null : __r, null, 2);
                    } catch (e) {
                        return 'Error: ' + (e && e.message ? e.message : String(e));
                    }
                })()
            """.trimIndent()
            val out = evalOnActiveWebView(wrapped)
            _extractState.value = _extractState.value.copy(jsResult = out, isEvaluatingJs = false)
        }
    }

    private suspend fun evalOnActiveWebView(script: String): String {
        val tab = _workspace.value.activeTab
        if (tab.isHome) return "(Start Page active — navigate to a webpage first)"
        val webView = tabWebViews[tab.id] ?: return "(WebView not loaded yet)"
        val deferred = CompletableDeferred<String>()
        withContext(Dispatchers.Main) {
            webView.evaluateJavascript(script) { raw ->
                deferred.complete(decodeJsStringResult(raw))
            }
        }
        return withTimeoutOrNull(8000L) { deferred.await() } ?: "(JavaScript evaluation timed out)"
    }

    private fun decodeJsStringResult(raw: String?): String {
        if (raw == null || raw == "null") return ""
        return runCatching {
            val element = Json.parseToJsonElement(raw)
            if (element is JsonPrimitive && element.isString) element.content else element.toString()
        }.getOrElse { raw }
    }

    // --- Parallel Batch Scrape ---

    fun updateScrapeUrls(text: String) {
        _scrapeState.value = _scrapeState.value.copy(urlsText = text)
    }

    fun updateScrapeConcurrency(concurrency: Int) {
        _scrapeState.value = _scrapeState.value.copy(concurrency = concurrency.coerceIn(1, 10))
    }

    fun updateScrapeMode(mode: ScrapeMode) {
        _scrapeState.value = _scrapeState.value.copy(mode = mode)
    }

    fun startBatchScrape() {
        scrapeJob?.cancel()
        val urls = _scrapeState.value.urlsText
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .distinct()
            .toList()
        if (urls.isEmpty()) {
            _statusBanner.value = "Enter at least one URL to scrape."
            return
        }
        val initial = urls.map { ScrapeJobResult(url = it, status = ScrapeStatus.QUEUED) }
        _scrapeState.value = _scrapeState.value.copy(
            isRunning = true,
            completedCount = 0,
            totalCount = urls.size,
            results = initial,
        )
        val cfg = settings.value
        val mode = _scrapeState.value.mode
        val semaphore = Semaphore(_scrapeState.value.concurrency)

        scrapeJob = viewModelScope.launch(Dispatchers.IO) {
            val jobs = urls.mapIndexed { idx, url ->
                async {
                    semaphore.withPermit {
                        updateScrapeItem(idx) { it.copy(status = ScrapeStatus.RUNNING) }
                        val resp = httpClient.fetchRaw(url, cfg) { reqUrl, status, rule ->
                            if (rule != null) {
                                recordNetworkLog("GET", reqUrl, "scrape", NetworkOutcome.BLOCKED_TRACKER, null, "Rule: $rule")
                            } else {
                                recordNetworkLog("GET", reqUrl, "scrape", NetworkOutcome.ALLOWED, status, null)
                            }
                        }
                        val res = if (resp.error != null) {
                            val blocked = resp.error.contains("SSRF", true) ||
                                resp.error.contains("blocklist", true) ||
                                resp.error.contains("robots", true)
                            ScrapeJobResult(
                                url = resp.finalUrl,
                                status = if (blocked) ScrapeStatus.BLOCKED else ScrapeStatus.FAILED,
                                httpStatus = resp.statusCode.takeIf { it > 0 },
                                elapsedMs = resp.elapsedMs,
                                blockedTrackers = resp.blockedTrackers,
                                error = resp.error,
                            )
                        } else {
                            ScrapeJobResult(
                                url = resp.finalUrl,
                                status = ScrapeStatus.SUCCESS,
                                httpStatus = resp.statusCode,
                                title = httpClient.extractTitle(resp.body),
                                elapsedMs = resp.elapsedMs,
                                blockedTrackers = resp.blockedTrackers,
                                output = httpClient.formatScrapedOutput(resp.body, resp.finalUrl, mode),
                            )
                        }
                        updateScrapeItem(idx) { res }
                        _scrapeState.update { it.copy(completedCount = it.completedCount + 1) }
                    }
                }
            }
            jobs.awaitAll()
            _scrapeState.update { it.copy(isRunning = false) }
        }
    }

    fun stopBatchScrape() {
        scrapeJob?.cancel()
        _scrapeState.update { it.copy(isRunning = false) }
    }

    private fun updateScrapeItem(index: Int, transform: (ScrapeJobResult) -> ScrapeJobResult) {
        _scrapeState.update { current ->
            val list = current.results.toMutableList()
            if (index in list.indices) {
                list[index] = transform(list[index])
                current.copy(results = list)
            } else {
                current
            }
        }
    }

    fun formatScrapeExport(asJson: Boolean): String {
        val results = _scrapeState.value.results
        return if (asJson) {
            buildJsonArray {
                results.forEach { r ->
                    add(buildJsonObject {
                        put("url", r.url)
                        put("status", r.status.name)
                        put("httpStatus", r.httpStatus)
                        put("title", r.title)
                        put("elapsedMs", r.elapsedMs)
                        put("output", r.output)
                        put("error", r.error)
                    })
                }
            }.toString()
        } else {
            results.joinToString("\n") { r ->
                val cleanOut = (r.error ?: r.output).replace('\n', ' ').replace('\t', ' ')
                "${r.url}\t${r.httpStatus ?: 0}\t${r.elapsedMs}ms\t$cleanOut"
            }
        }
    }

    // --- Logs, Bridge & Diagnostics ---

    fun recordConsoleMessage(level: String, message: String, source: String) {
        val entry = ConsoleLogEntry(logIdCounter.getAndIncrement(), timeFormat.format(Date()), level, message, source)
        _consoleLogs.update { (listOf(entry) + it).take(200) }
    }

    private fun recordNetworkLog(
        method: String,
        url: String,
        resourceType: String,
        outcome: NetworkOutcome,
        statusCode: Int?,
        detail: String?,
    ) {
        val host = runCatching { URI(url).host.orEmpty() }.getOrDefault("")
        val entry = NetworkLogEntry(
            id = logIdCounter.getAndIncrement(),
            timestamp = timeFormat.format(Date()),
            method = method,
            url = url,
            host = host,
            resourceType = resourceType,
            outcome = outcome,
            statusCode = statusCode,
            detail = detail,
        )
        _networkLogs.update { (listOf(entry) + it).take(300) }
    }

    fun clearNetworkLogs() {
        _networkLogs.value = emptyList()
    }

    fun clearConsoleLogs() {
        _consoleLogs.value = emptyList()
    }

    fun toggleBridgeServer() {
        if (_bridgeState.value.running) mcpBridge.stop() else mcpBridge.start(settings.value.bridgePort)
    }

    fun updateSettings(transform: (ObscuraSettings) -> ObscuraSettings) {
        val previous = settings.value
        val updated = transform(previous)
        viewModelScope.launch {
            settingsRepo.updateSettings(transform)
        }

        val activeTab = _workspace.value.activeTab
        if (!activeTab.isHome && activeTab.blockedMessage == null) {
            val activeWebView = tabWebViews[activeTab.id]
            val desktopChanged = updated.desktopMode != previous.desktopMode
            val stealthOrProfileChanged = updated.stealthEnabled != previous.stealthEnabled ||
                updated.selectedProfileIndex != previous.selectedProfileIndex ||
                updated.customUserAgent != previous.customUserAgent

            if (desktopChanged && activeWebView != null) {
                applyWebViewSettings(activeWebView, updated)
                tabLoadStarts[activeTab.id] = System.currentTimeMillis()
                (activeWebView as? ObscuraWebView)?.isPullEnabled = false
                activeWebView.reload()
                _workspace.update {
                    if (it.overlayMode is BrowserOverlayMode.ActionSheet) it.copy(overlayMode = BrowserOverlayMode.None) else it
                }
                _statusBanner.value = if (updated.desktopMode) "Switched to Desktop site" else "Switched to Mobile site"
            } else if (stealthOrProfileChanged) {
                applyWebViewSettings(activeWebView, updated)
                _statusBanner.value = "Profile updated — reload page to apply JS hooks"
            }
        }
    }

    fun runDiagnosticCheck(input: String) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val urlToTest = if (trimmed.contains("://")) trimmed else "https://$trimmed"
            val ssrf = SsrfGuard.validateUrl(urlToTest, allowPrivateNetwork = false)
            val host = runCatching { URI(urlToTest).host ?: trimmed }.getOrDefault(trimmed)
            val matchedRule = trackerBlocklist.matchedRule(host)
            _diagnosticResult.value = DiagnosticCheckResult(
                input = trimmed,
                ssrfForbidden = !ssrf.allowed,
                ssrfReason = ssrf.reason,
                trackerBlocked = matchedRule != null,
                trackerMatch = matchedRule ?: "Not on blocklist",
            )
        }
    }

    fun clearCookies() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        _statusBanner.value = "All browser cookies cleared."
    }

    fun clearAllBrowsingData() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        tabWebViews.values.forEach { it.clearCache(true) }
        viewModelScope.launch { settingsRepo.saveHistory(emptyList()) }
        _statusBanner.value = "Cookies, cache, and history cleared."
    }

    fun shareText(title: String, content: String) {
        val ctx = getApplication<Application>()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, content)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun updateTab(tabId: String, transform: (BrowserTabState) -> BrowserTabState) {
        _workspace.update { state ->
            state.copy(tabs = state.tabs.map { if (it.id == tabId) transform(it) else it })
        }
    }

    override fun onCleared() {
        mcpBridge.stop()
        tabNavJobs.values.forEach { it.cancel() }
        tabNavJobs.clear()
        tabWebViews.values.forEach { it.destroy() }
        tabWebViews.clear()
        super.onCleared()
    }
}
