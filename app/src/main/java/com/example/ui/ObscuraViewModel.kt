package com.example.ui

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppTab
import com.example.data.ConsoleLogEntry
import com.example.data.DiagnosticCheckResult
import com.example.data.DumpFormat
import com.example.data.InteractiveElementRef
import com.example.data.NetworkLogEntry
import com.example.data.NetworkOutcome
import com.example.data.ObscuraSettings
import com.example.data.ScrapeJobResult
import com.example.data.ScrapeMode
import com.example.data.ScrapeStatus
import com.example.data.ServerBridgeState
import com.example.data.SettingsRepository
import com.example.data.WaitUntilMode
import com.example.engine.BrowserProfiles
import com.example.engine.ExtractionScripts
import com.example.engine.LocalMcpBridge
import com.example.engine.ObscuraHttpClient
import com.example.engine.RobotsCache
import com.example.engine.SsrfGuard
import com.example.engine.TrackerBlocklist
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

data class BrowserUiState(
    val urlInput: String = "https://example.com",
    val currentUrl: String = "https://example.com",
    val pageTitle: String = "Obscura Stealth Browser",
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val lastLoadMs: Long = 0L,
    val lastHttpStatus: Int? = null,
    val pageBlockedTrackers: Int = 0,
    val blockedMessage: String? = null,
    val hasLoadedOnce: Boolean = false,
    val screenshotBitmap: Bitmap? = null,
)

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
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val logIdCounter = AtomicLong(1L)
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    val settings: StateFlow<ObscuraSettings> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, ObscuraSettings())

    private val _activeTab = MutableStateFlow(AppTab.Browser)
    val activeTab: StateFlow<AppTab> = _activeTab.asStateFlow()

    private val _browserState = MutableStateFlow(BrowserUiState())
    val browserState: StateFlow<BrowserUiState> = _browserState.asStateFlow()

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

    private var activeWebView: WebView? = null
    private var pageLoadStartMs: Long = 0L
    private var scrapeJob: Job? = null

    private val mcpBridge = LocalMcpBridge(
        scope = viewModelScope,
        onNavigate = { url ->
            withContext(Dispatchers.Main) {
                navigateTo(url)
            }
            "Navigated to $url"
        },
        onSnapshot = {
            withContext(Dispatchers.Main) {
                val title = _browserState.value.pageTitle
                val url = _browserState.value.currentUrl
                val text = evalOnWebViewSuspend(ExtractionScripts.EXTRACT_TEXT_JS).take(2000)
                val refsJson = evalOnWebViewSuspend(ExtractionScripts.EXTRACT_SNAPSHOT_JS)
                "URL: $url\nTitle: $title\nRefs: $refsJson\nBody:\n$text"
            }
        },
        onEvaluate = { expr ->
            withContext(Dispatchers.Main) {
                evalOnWebViewSuspend(expr)
            }
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

    fun selectTab(tab: AppTab) {
        _activeTab.value = tab
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    fun attachWebView(webView: WebView) {
        activeWebView = webView
    }

    fun updateUrlInput(value: String) {
        _browserState.value = _browserState.value.copy(urlInput = value)
    }

    fun navigateTo(rawUrl: String) {
        val url = httpClient.normalizeUrl(rawUrl)
        _browserState.value = _browserState.value.copy(
            urlInput = url,
            blockedMessage = null,
            pageBlockedTrackers = 0,
        )

        viewModelScope.launch {
            val currentSettings = settings.value
            val ssrf = withContext(Dispatchers.IO) {
                SsrfGuard.validateUrl(url, currentSettings.allowPrivateNetwork)
            }
            if (!ssrf.allowed) {
                recordNetworkLog("GET", url, "document", NetworkOutcome.BLOCKED_SSRF, null, ssrf.reason)
                _browserState.value = _browserState.value.copy(
                    isLoading = false,
                    blockedMessage = ssrf.reason,
                )
                return@launch
            }

            val host = runCatching { URI(url).host.orEmpty() }.getOrDefault("")
            if (currentSettings.stealthEnabled) {
                val rule = trackerBlocklist.matchedRule(host)
                if (rule != null) {
                    val msg = "Blocked by stealth tracker blocklist ($rule)"
                    recordNetworkLog("GET", url, "document", NetworkOutcome.BLOCKED_TRACKER, null, msg)
                    _browserState.value = _browserState.value.copy(
                        isLoading = false,
                        blockedMessage = msg,
                    )
                    return@launch
                }
            }

            val (robotsAllowed, robotsReason) = httpClient.ensureRobotsAllowed(url, currentSettings)
            if (!robotsAllowed) {
                recordNetworkLog("GET", url, "document", NetworkOutcome.BLOCKED_ROBOTS, null, robotsReason)
                _browserState.value = _browserState.value.copy(
                    isLoading = false,
                    blockedMessage = robotsReason ?: "Disallowed by robots.txt",
                )
                return@launch
            }

            pageLoadStartMs = System.currentTimeMillis()
            _browserState.value = _browserState.value.copy(
                currentUrl = url,
                isLoading = true,
                progress = 10,
                hasLoadedOnce = true,
            )
            applyWebViewSettings(activeWebView, currentSettings)
            activeWebView?.loadUrl(url)
        }
    }

    fun applyWebViewSettings(webView: WebView?, currentSettings: ObscuraSettings) {
        webView ?: return
        val ua = BrowserProfiles.effectiveUserAgent(
            currentSettings.selectedProfileIndex,
            currentSettings.customUserAgent,
        )
        if (webView.settings.userAgentString != ua) {
            webView.settings.userAgentString = ua
        }
    }

    fun onPageStarted(url: String) {
        pageLoadStartMs = System.currentTimeMillis()
        _browserState.value = _browserState.value.copy(
            currentUrl = url,
            urlInput = url,
            isLoading = true,
            blockedMessage = null,
            hasLoadedOnce = true,
        )
    }

    fun onPageProgress(progress: Int) {
        _browserState.value = _browserState.value.copy(progress = progress)
    }

    fun onPageTitle(title: String) {
        if (title.isNotBlank()) {
            _browserState.value = _browserState.value.copy(pageTitle = title)
        }
    }

    fun onPageFinished(webView: WebView, url: String) {
        val elapsed = if (pageLoadStartMs > 0) System.currentTimeMillis() - pageLoadStartMs else 0L
        val currentSettings = settings.value
        if (currentSettings.stealthEnabled) {
            val profile = BrowserProfiles.getProfile(currentSettings.selectedProfileIndex)
            webView.evaluateJavascript(BrowserProfiles.buildStealthBootstrapJs(profile), null)
        }
        _browserState.value = _browserState.value.copy(
            currentUrl = url,
            urlInput = url,
            isLoading = false,
            progress = 100,
            lastLoadMs = elapsed,
            canGoBack = webView.canGoBack(),
            canGoForward = webView.canGoForward(),
            hasLoadedOnce = true,
        )
        recordNetworkLog("GET", url, "document", NetworkOutcome.ALLOWED, 200, "${elapsed}ms")
    }

    fun shouldBlockSubresource(url: String, resourceType: String): NetworkOutcome? {
        val currentSettings = settings.value
        val host = runCatching { URI(url).host.orEmpty() }.getOrDefault("")
        if (!currentSettings.allowPrivateNetwork && host.isNotEmpty() && SsrfGuard.isForbiddenHostLiteral(host)) {
            recordNetworkLog("GET", url, resourceType, NetworkOutcome.BLOCKED_SSRF, null, "SSRF literal host blocked")
            return NetworkOutcome.BLOCKED_SSRF
        }
        if (currentSettings.stealthEnabled && host.isNotEmpty()) {
            val rule = trackerBlocklist.matchedRule(host)
            if (rule != null) {
                recordNetworkLog("GET", url, resourceType, NetworkOutcome.BLOCKED_TRACKER, null, "Rule: $rule")
                _browserState.value = _browserState.value.copy(
                    pageBlockedTrackers = _browserState.value.pageBlockedTrackers + 1,
                )
                return NetworkOutcome.BLOCKED_TRACKER
            }
        }
        recordNetworkLog("GET", url, resourceType, NetworkOutcome.ALLOWED, null, null)
        return null
    }

    fun recordConsoleMessage(level: String, message: String, source: String) {
        val entry = ConsoleLogEntry(
            id = logIdCounter.getAndIncrement(),
            timestamp = timeFormat.format(Date()),
            level = level,
            message = message,
            source = source,
        )
        _consoleLogs.value = (listOf(entry) + _consoleLogs.value).take(200)
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
        _networkLogs.value = (listOf(entry) + _networkLogs.value).take(300)
    }

    fun goBack() {
        activeWebView?.takeIf { it.canGoBack() }?.goBack()
    }

    fun goForward() {
        activeWebView?.takeIf { it.canGoForward() }?.goForward()
    }

    fun reload() {
        val url = _browserState.value.currentUrl
        if (activeWebView != null && _browserState.value.hasLoadedOnce) {
            pageLoadStartMs = System.currentTimeMillis()
            activeWebView?.reload()
        } else {
            navigateTo(url)
        }
    }

    fun captureScreenshot() {
        val webView = activeWebView ?: return
        if (webView.width <= 0 || webView.height <= 0) {
            _statusBanner.value = "Open the Browser tab and load a page first."
            return
        }
        val bitmap = Bitmap.createBitmap(webView.width, webView.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        webView.draw(canvas)
        _browserState.value = _browserState.value.copy(screenshotBitmap = bitmap)
    }

    fun dismissScreenshot() {
        _browserState.value = _browserState.value.copy(screenshotBitmap = null)
    }

    fun shareScreenshot(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, "obscura_capture_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
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

    fun exportPdf(activityContext: android.content.Context) {
        val webView = activeWebView ?: return
        val printManager = activityContext.getSystemService(android.content.Context.PRINT_SERVICE) as? PrintManager
            ?: return
        val jobName = "Obscura_${_browserState.value.pageTitle.take(24)}"
        val adapter = webView.createPrintDocumentAdapter(jobName)
        printManager.print(jobName, adapter, PrintAttributes.Builder().build())
    }

    // --- Extract Tab (--dump / --eval / snapshot) ---

    fun selectDumpFormat(format: DumpFormat) {
        _extractState.value = _extractState.value.copy(selectedFormat = format)
        runExtraction(format)
    }

    fun runExtraction(format: DumpFormat = _extractState.value.selectedFormat) {
        _extractState.value = _extractState.value.copy(isExtracting = true)
        viewModelScope.launch {
            val result = when (format) {
                DumpFormat.MARKDOWN -> evalOnWebViewSuspend(ExtractionScripts.HTML_TO_MARKDOWN_JS)
                DumpFormat.TEXT -> evalOnWebViewSuspend(ExtractionScripts.EXTRACT_TEXT_JS)
                DumpFormat.LINKS -> evalOnWebViewSuspend(ExtractionScripts.EXTRACT_LINKS_JS)
                DumpFormat.HTML -> evalOnWebViewSuspend(ExtractionScripts.EXTRACT_HTML_JS)
                DumpFormat.ASSETS -> evalOnWebViewSuspend(ExtractionScripts.EXTRACT_ASSETS_JS)
                DumpFormat.COOKIES -> {
                    val url = _browserState.value.currentUrl
                    val cookieStr = CookieManager.getInstance().getCookie(url).orEmpty()
                    if (cookieStr.isBlank()) "[]" else {
                        val pairs = cookieStr.split(";").map { it.trim() }.filter { it.contains("=") }
                        buildJsonArray {
                            pairs.forEach { p ->
                                val idx = p.indexOf('=')
                                add(buildJsonObject {
                                    put("name", p.substring(0, idx).trim())
                                    put("value", p.substring(idx + 1).trim())
                                    put("url", url)
                                })
                            }
                        }.toString()
                    }
                }
                DumpFormat.SNAPSHOT -> {
                    val rawJson = evalOnWebViewSuspend(ExtractionScripts.EXTRACT_SNAPSHOT_JS)
                    val parsed = runCatching {
                        json.decodeFromString<List<InteractiveElementRef>>(rawJson)
                    }.getOrDefault(emptyList())
                    _extractState.value = _extractState.value.copy(snapshotRefs = parsed)
                    rawJson
                }
                DumpFormat.ORIGINAL -> {
                    val resp = httpClient.fetchRaw(_browserState.value.currentUrl, settings.value)
                    if (resp.error != null) "Error: ${resp.error}" else resp.body
                }
            }
            _extractState.value = _extractState.value.copy(
                extractedOutput = result.ifBlank { "(empty output — load a page in Browser tab first)" },
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
            val out = evalOnWebViewSuspend(wrapped)
            _extractState.value = _extractState.value.copy(
                jsResult = out,
                isEvaluatingJs = false,
            )
        }
    }

    private suspend fun evalOnWebViewSuspend(script: String): String {
        val webView = activeWebView ?: return "(WebView not attached yet — open Browser tab first)"
        val deferred = CompletableDeferred<String>()
        withContext(Dispatchers.Main) {
            webView.evaluateJavascript(script) { raw ->
                val decoded = decodeJsStringResult(raw)
                deferred.complete(decoded)
            }
        }
        return withTimeoutOrNull(8000L) { deferred.await() } ?: "(JavaScript evaluation timed out)"
    }

    private fun decodeJsStringResult(raw: String?): String {
        if (raw == null || raw == "null") return ""
        return runCatching {
            val element = Json.parseToJsonElement(raw)
            if (element is JsonPrimitive && element.isString) {
                element.content
            } else {
                element.toString()
            }
        }.getOrElse { raw }
    }

    // --- Scrape Tab (Parallel Batch Scrape) ---

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

        val initialResults = urls.map { ScrapeJobResult(url = it, status = ScrapeStatus.QUEUED) }
        _scrapeState.value = _scrapeState.value.copy(
            isRunning = true,
            completedCount = 0,
            totalCount = urls.size,
            results = initialResults,
        )

        val currentSettings = settings.value
        val mode = _scrapeState.value.mode
        val semaphore = Semaphore(_scrapeState.value.concurrency)

        scrapeJob = viewModelScope.launch(Dispatchers.IO) {
            val jobs = urls.mapIndexed { index, url ->
                async {
                    semaphore.withPermit {
                        updateScrapeItem(index) { it.copy(status = ScrapeStatus.RUNNING) }
                        val resp = httpClient.fetchRaw(url, currentSettings) { reqUrl, status, rule ->
                            if (rule != null) {
                                recordNetworkLog("GET", reqUrl, "scrape", NetworkOutcome.BLOCKED_TRACKER, null, "Rule: $rule")
                            } else {
                                recordNetworkLog("GET", reqUrl, "scrape", NetworkOutcome.ALLOWED, status, null)
                            }
                        }
                        val result = if (resp.error != null) {
                            val blocked = resp.error.contains("SSRF", ignoreCase = true) ||
                                resp.error.contains("blocklist", ignoreCase = true) ||
                                resp.error.contains("robots", ignoreCase = true)
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
                        updateScrapeItem(index) { result }
                        _scrapeState.value = _scrapeState.value.copy(
                            completedCount = _scrapeState.value.completedCount + 1,
                        )
                    }
                }
            }
            jobs.awaitAll()
            _scrapeState.value = _scrapeState.value.copy(isRunning = false)
        }
    }

    fun stopBatchScrape() {
        scrapeJob?.cancel()
        _scrapeState.value = _scrapeState.value.copy(isRunning = false)
    }

    private fun updateScrapeItem(index: Int, transform: (ScrapeJobResult) -> ScrapeJobResult) {
        val list = _scrapeState.value.results.toMutableList()
        if (index in list.indices) {
            list[index] = transform(list[index])
            _scrapeState.value = _scrapeState.value.copy(results = list)
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

    // --- Network & Bridge ---

    fun clearNetworkLogs() {
        _networkLogs.value = emptyList()
    }

    fun clearConsoleLogs() {
        _consoleLogs.value = emptyList()
    }

    fun toggleBridgeServer() {
        if (_bridgeState.value.running) {
            mcpBridge.stop()
        } else {
            mcpBridge.start(settings.value.bridgePort)
        }
    }

    // --- Settings & Diagnostics ---

    fun updateSettings(transform: (ObscuraSettings) -> ObscuraSettings) {
        viewModelScope.launch {
            settingsRepo.updateSettings(transform)
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

    override fun onCleared() {
        mcpBridge.stop()
        super.onCleared()
    }
}
