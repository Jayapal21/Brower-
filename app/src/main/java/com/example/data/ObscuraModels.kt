package com.example.data

import kotlinx.serialization.Serializable

@Serializable
enum class AppTab(val label: String) {
    Browser("Browser"),
    Extract("Extract"),
    Scrape("Scrape"),
    Network("Network"),
    Stealth("Stealth"),
}

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
    val selectedProfileIndex: Int = 0,
    val customUserAgent: String = "",
    val proxyUrl: String = "",
    val timeoutSeconds: Int = 30,
    val waitUntil: WaitUntilMode = WaitUntilMode.LOAD,
    val darkTheme: Boolean = true,
    val bridgePort: Int = 9222,
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
