package com.example.data

import android.graphics.Bitmap
import com.example.motion.MotionModePreference
import kotlinx.serialization.Serializable

/**
 * Contextual workspace views opened from the browser menu or toolbar (no bottom navigation).
 */
enum class BrowserDestination {
    BROWSER,
    BOOKMARKS,
    DOWNLOADS,
    HISTORY,
    EXTRACT,
    SCRAPE,
    NETWORK,
    SETTINGS,
}

enum class SearchEngineOption(val label: String, val searchUrlTemplate: String) {
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q="),
    GOOGLE("Google", "https://www.google.com/search?q="),
    STARTPAGE("Startpage", "https://www.startpage.com/sp/search?query="),
    BRAVE("Brave Search", "https://search.brave.com/search?q="),
}

data class BrowserTabState(
    val id: String,
    val url: String = "about:home",
    val title: String = "Start Page",
    val isHome: Boolean = true,
    val isPrivate: Boolean = false,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val lastLoadMs: Long = 0L,
    val blockedTrackers: Int = 0,
    val blockedMessage: String? = null,
)

sealed interface BrowserOverlayMode {
    data object None : BrowserOverlayMode
    data object AddressFocus : BrowserOverlayMode
    data object TabSwitcher : BrowserOverlayMode
    data object ActionSheet : BrowserOverlayMode
    data object FindInPage : BrowserOverlayMode
    data class ScreenshotPreview(val bitmap: Bitmap) : BrowserOverlayMode
}

data class BrowserWorkspaceState(
    val tabs: List<BrowserTabState> = listOf(BrowserTabState(id = "tab-1")),
    val activeTabId: String = "tab-1",
    val urlInput: String = "",
    val overlayMode: BrowserOverlayMode = BrowserOverlayMode.None,
    val findQuery: String = "",
    val findMatchIndex: Int = 0,
    val findTotalMatches: Int = 0,
    val toolbarVisible: Boolean = true,
) {
    val activeTab: BrowserTabState
        get() = tabs.firstOrNull { it.id == activeTabId } ?: tabs.first()

    val isAddressFocused: Boolean
        get() = overlayMode is BrowserOverlayMode.AddressFocus

    val isTabSwitcherOpen: Boolean
        get() = overlayMode is BrowserOverlayMode.TabSwitcher

    val isMenuOpen: Boolean
        get() = overlayMode is BrowserOverlayMode.ActionSheet

    val isFindInPageOpen: Boolean
        get() = overlayMode is BrowserOverlayMode.FindInPage

    val screenshotBitmap: Bitmap?
        get() = (overlayMode as? BrowserOverlayMode.ScreenshotPreview)?.bitmap
}

@Serializable
data class BookmarkItem(
    val id: String,
    val title: String,
    val url: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class HistoryItem(
    val id: String,
    val title: String,
    val url: String,
    val visitedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class WebsiteShortcut(
    val id: String,
    val title: String,
    val url: String,
    val badge: String,
)

enum class DownloadStatus {
    IN_PROGRESS,
    COMPLETED,
    FAILED,
}

@Serializable
data class DownloadItem(
    val id: Long,
    val fileName: String,
    val url: String,
    val mimeType: String,
    val sizeBytes: Long = 0L,
    val status: DownloadStatus = DownloadStatus.IN_PROGRESS,
    val createdAt: Long = System.currentTimeMillis(),
    val localUri: String? = null,
)

enum class SuggestionKind {
    BOOKMARK,
    HISTORY,
    SHORTCUT,
    DIRECT_URL,
    WEB_SEARCH,
}

data class AddressSuggestion(
    val title: String,
    val subtitle: String,
    val targetUrl: String,
    val kind: SuggestionKind,
)

enum class DumpFormat(val label: String, val cliFlag: String) {
    MARKDOWN("Markdown", "markdown"),
    TEXT("Text", "text"),
    LINKS("Links", "links"),
    HTML("HTML", "html"),
    ASSETS("Assets", "assets"),
    COOKIES("Cookies", "cookies"),
    SNAPSHOT("Snapshot", "snapshot"),
    ORIGINAL("Original", "original"),
}

enum class WaitUntilMode(val label: String, val value: String) {
    LOAD("Load", "load"),
    DOM_CONTENT_LOADED("DOM Ready", "domcontentloaded"),
    NETWORK_IDLE("Network Idle", "networkidle0"),
}

enum class NetworkOutcome {
    ALLOWED,
    BLOCKED_TRACKER,
    BLOCKED_SSRF,
    BLOCKED_ROBOTS,
    ERROR,
}

data class NetworkLogEntry(
    val id: Long,
    val timestamp: String,
    val method: String,
    val url: String,
    val host: String,
    val resourceType: String,
    val outcome: NetworkOutcome,
    val statusCode: Int? = null,
    val detail: String? = null,
)

data class ConsoleLogEntry(
    val id: Long,
    val timestamp: String,
    val level: String,
    val message: String,
    val source: String,
)

@Serializable
data class InteractiveElementRef(
    val ref: String,
    val tag: String,
    val text: String,
    val href: String = "",
    val type: String = "",
)

enum class ScrapeMode(val label: String) {
    SUMMARY("Title & Meta"),
    MARKDOWN("Markdown"),
    TEXT("Plain Text"),
    LINKS("Links"),
    RAW_HTML("Raw HTML"),
}

enum class ScrapeStatus {
    QUEUED,
    RUNNING,
    SUCCESS,
    BLOCKED,
    FAILED,
}

data class ScrapeJobResult(
    val url: String,
    val status: ScrapeStatus,
    val httpStatus: Int? = null,
    val title: String = "",
    val elapsedMs: Long = 0L,
    val blockedTrackers: Int = 0,
    val output: String = "",
    val error: String? = null,
)

data class ObscuraSettings(
    val stealthEnabled: Boolean = true,
    val allowPrivateNetwork: Boolean = false,
    val obeyRobots: Boolean = false,
    val desktopMode: Boolean = false,
    val selectedProfileIndex: Int = 0,
    val customUserAgent: String = "",
    val proxyUrl: String = "",
    val timeoutSeconds: Int = 30,
    val waitUntil: WaitUntilMode = WaitUntilMode.LOAD,
    val darkTheme: Boolean = true,
    val bridgePort: Int = 9222,
    val searchEngine: SearchEngineOption = SearchEngineOption.DUCKDUCKGO,
    val motionMode: MotionModePreference = MotionModePreference.AUTO,
    val springStiffness: Float = 1100f,
    val springDamping: Float = 0.85f,
)

data class ServerBridgeState(
    val running: Boolean = false,
    val port: Int = 9222,
    val requestCount: Int = 0,
    val lastCall: String? = null,
    val error: String? = null,
)

data class DiagnosticCheckResult(
    val input: String,
    val ssrfForbidden: Boolean,
    val ssrfReason: String,
    val trackerBlocked: Boolean,
    val trackerMatch: String,
)
