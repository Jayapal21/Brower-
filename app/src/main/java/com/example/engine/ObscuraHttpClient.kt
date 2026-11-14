package com.example.engine

import com.example.data.ObscuraSettings
import com.example.data.ScrapeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.util.concurrent.TimeUnit

data class FetchHttpResponse(
    val finalUrl: String,
    val statusCode: Int,
    val contentType: String,
    val body: String,
    val elapsedMs: Long,
    val blockedTrackers: Int = 0,
    val error: String? = null,
)

class ObscuraHttpClient(
    private val trackerBlocklist: TrackerBlocklist,
    private val robotsCache: RobotsCache,
) {

    private val baseClient: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
        .dispatcher(Dispatcher().apply {
            maxRequests = 32
            maxRequestsPerHost = 8
        })
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return "https://example.com"
        return if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("about:", ignoreCase = true) ||
            trimmed.startsWith("data:", ignoreCase = true)
        ) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    internal fun configuredClient(
        settings: ObscuraSettings,
        timeoutSecondsOverride: Int? = null,
    ): OkHttpClient {
        val timeoutSec = (timeoutSecondsOverride ?: settings.timeoutSeconds).coerceIn(5, 120).toLong()
        val builder = baseClient.newBuilder()
            .connectTimeout(timeoutSec, TimeUnit.SECONDS)
            .readTimeout(timeoutSec, TimeUnit.SECONDS)
            .writeTimeout(timeoutSec, TimeUnit.SECONDS)

        if (!settings.allowPrivateNetwork) {
            builder.dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> {
                    val resolved = Dns.SYSTEM.lookup(hostname)
                    for (addr in resolved) {
                        if (SsrfGuard.isForbiddenIp(addr)) {
                            throw IOException("SSRF blocked: $hostname resolved to forbidden IP ${addr.hostAddress}")
                        }
                    }
                    return resolved
                }
            })
        }

        parseProxy(settings.proxyUrl)?.let { proxy ->
            builder.proxy(proxy)
        }

        return builder.build()
    }

    private fun parseProxy(proxyUrl: String): Proxy? {
        val trimmed = proxyUrl.trim()
        if (trimmed.isEmpty()) return null
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else when (uri.scheme?.lowercase()) {
            "socks", "socks5" -> 1080
            else -> 8080
        }
        val type = when (uri.scheme?.lowercase()) {
            "socks", "socks5", "socks4" -> Proxy.Type.SOCKS
            else -> Proxy.Type.HTTP
        }
        return Proxy(type, InetSocketAddress(host, port))
    }

    suspend fun ensureRobotsAllowed(
        urlString: String,
        settings: ObscuraSettings,
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        if (!settings.obeyRobots) return@withContext true to null
        val uri = runCatching { URI(urlString) }.getOrNull() ?: return@withContext true to null
        val scheme = uri.scheme?.lowercase() ?: return@withContext true to null
        if (scheme != "http" && scheme != "https") return@withContext true to null
        val host = uri.host ?: return@withContext true to null
        val portPart = if (uri.port != -1) ":${uri.port}" else ""
        val origin = "$scheme://$host$portPart"
        val userAgent = BrowserProfiles.effectiveUserAgent(
            settings.selectedProfileIndex,
            settings.customUserAgent,
        )

        if (!robotsCache.contains(origin)) {
            val robotsUrl = "$origin/robots.txt"
            runCatching {
                val client = configuredClient(settings)
                val req = Request.Builder()
                    .url(robotsUrl)
                    .header("User-Agent", userAgent)
                    .build()
                client.newCall(req).execute().use { resp ->
                    val body = if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
                    robotsCache.parseAndStore(origin, body, userAgent)
                }
            }.onFailure {
                robotsCache.parseAndStore(origin, "", userAgent)
            }
        }

        val path = buildString {
            append(uri.rawPath?.ifEmpty { "/" } ?: "/")
            if (!uri.rawQuery.isNullOrEmpty()) {
                append('?').append(uri.rawQuery)
            }
        }
        val allowed = robotsCache.isAllowed(origin, path)
        if (!allowed) {
            false to "Blocked by robots.txt policy for $origin$path"
        } else {
            true to null
        }
    }

    suspend fun fetchRaw(
        rawUrl: String,
        settings: ObscuraSettings,
        onNetworkEvent: ((url: String, statusCode: Int?, blockedRule: String?) -> Unit)? = null,
    ): FetchHttpResponse = withContext(Dispatchers.IO) {
        val url = normalizeUrl(rawUrl)
        val startMs = System.currentTimeMillis()

        val ssrf = SsrfGuard.validateUrl(url, settings.allowPrivateNetwork)
        if (!ssrf.allowed) {
            return@withContext FetchHttpResponse(
                finalUrl = url,
                statusCode = 0,
                contentType = "",
                body = "",
                elapsedMs = System.currentTimeMillis() - startMs,
                error = ssrf.reason,
            )
        }

        val host = runCatching { URI(url).host.orEmpty() }.getOrDefault("")
        if (settings.stealthEnabled) {
            val rule = trackerBlocklist.matchedRule(host)
            if (rule != null) {
                onNetworkEvent?.invoke(url, null, rule)
                return@withContext FetchHttpResponse(
                    finalUrl = url,
                    statusCode = 0,
                    contentType = "",
                    body = "",
                    elapsedMs = System.currentTimeMillis() - startMs,
                    blockedTrackers = 1,
                    error = "Blocked by stealth tracker blocklist ($rule)",
                )
            }
        }

        val (robotsOk, robotsMsg) = ensureRobotsAllowed(url, settings)
        if (!robotsOk) {
            return@withContext FetchHttpResponse(
                finalUrl = url,
                statusCode = 0,
                contentType = "",
                body = "",
                elapsedMs = System.currentTimeMillis() - startMs,
                error = robotsMsg ?: "Blocked by robots.txt",
            )
        }

        val userAgent = BrowserProfiles.effectiveUserAgent(
            settings.selectedProfileIndex,
            settings.customUserAgent,
        )

        return@withContext try {
            val client = configuredClient(settings)
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                val finalUrl = response.request.url.toString()
                val contentType = response.header("Content-Type").orEmpty()
                onNetworkEvent?.invoke(finalUrl, response.code, null)
                FetchHttpResponse(
                    finalUrl = finalUrl,
                    statusCode = response.code,
                    contentType = contentType,
                    body = bodyString,
                    elapsedMs = System.currentTimeMillis() - startMs,
                )
            }
        } catch (e: Exception) {
            FetchHttpResponse(
                finalUrl = url,
                statusCode = 0,
                contentType = "",
                body = "",
                elapsedMs = System.currentTimeMillis() - startMs,
                error = e.message ?: "Request failed",
            )
        }
    }

    internal fun fetchHeadSnippet(
        rawUrl: String,
        settings: ObscuraSettings,
        maxBytes: Int = 96 * 1024,
    ): Pair<String, String>? {
        val url = normalizeUrl(rawUrl)
        val ssrf = SsrfGuard.validateUrl(url, settings.allowPrivateNetwork)
        if (!ssrf.allowed) return null

        val userAgent = BrowserProfiles.effectiveUserAgent(
            settings.selectedProfileIndex,
            settings.customUserAgent,
        )
        return runCatching {
            val client = configuredClient(settings, timeoutSecondsOverride = 8)
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body ?: return@runCatching null
                val contentType = response.header("Content-Type").orEmpty().lowercase()
                if (contentType.isNotEmpty() && !contentType.contains("html") && !contentType.contains("xml")) {
                    return@runCatching null
                }
                val source = body.byteStream()
                val buffer = ByteArray(4096)
                val out = java.io.ByteArrayOutputStream()
                var total = 0
                while (total < maxBytes) {
                    val read = source.read(buffer, 0, minOf(buffer.size, maxBytes - total))
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                    total += read
                    val partial = out.toString(Charsets.UTF_8.name())
                    if (partial.contains("</head>", ignoreCase = true)) {
                        return@runCatching response.request.url.toString() to partial
                    }
                }
                response.request.url.toString() to out.toString(Charsets.UTF_8.name())
            }
        }.getOrNull()
    }

    internal fun fetchBinaryBytes(
        rawUrl: String,
        settings: ObscuraSettings,
        maxBytes: Int = 512 * 1024,
    ): ByteArray? {
        val url = normalizeUrl(rawUrl)
        val ssrf = SsrfGuard.validateUrl(url, settings.allowPrivateNetwork)
        if (!ssrf.allowed) return null

        val userAgent = BrowserProfiles.effectiveUserAgent(
            settings.selectedProfileIndex,
            settings.customUserAgent,
        )
        return runCatching {
            val client = configuredClient(settings, timeoutSecondsOverride = 8)
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept", "image/png,image/webp,image/x-icon,image/vnd.microsoft.icon,image/*,*/*;q=0.5")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val contentType = response.header("Content-Type").orEmpty().lowercase()
                if (contentType.contains("text/html") || contentType.contains("svg")) {
                    return@runCatching null
                }
                val body = response.body ?: return@runCatching null
                val declaredLen = body.contentLength()
                if (declaredLen > maxBytes) return@runCatching null
                val stream = body.byteStream()
                val buffer = ByteArray(4096)
                val out = java.io.ByteArrayOutputStream()
                var total = 0
                while (total < maxBytes) {
                    val read = stream.read(buffer, 0, minOf(buffer.size, maxBytes - total))
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                    total += read
                }
                if (total == 0) null else out.toByteArray()
            }
        }.getOrNull()
    }

    fun extractTitle(html: String): String {
        val match = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)
        return decodeHtmlEntities(match?.groupValues?.getOrNull(1)?.trim().orEmpty())
            .replace(Regex("\\s+"), " ")
    }

    fun formatScrapedOutput(html: String, baseUrl: String, mode: ScrapeMode): String {
        return when (mode) {
            ScrapeMode.RAW_HTML -> html
            ScrapeMode.SUMMARY -> {
                val title = extractTitle(html).ifEmpty { "(no title)" }
                val desc = Regex(
                    "<meta[^>]+name=[\"']description[\"'][^>]+content=[\"']([^\"']*)[\"']",
                    RegexOption.IGNORE_CASE,
                ).find(html)?.groupValues?.getOrNull(1).orEmpty()
                val h1 = Regex("<h1[^>]*>(.*?)</h1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    .find(html)?.groupValues?.getOrNull(1)?.let { stripTags(it) }.orEmpty()
                buildString {
                    appendLine("Title: $title")
                    if (h1.isNotEmpty()) appendLine("H1: $h1")
                    if (desc.isNotEmpty()) appendLine("Description: ${decodeHtmlEntities(desc)}")
                    appendLine("URL: $baseUrl")
                    appendLine("Size: ${html.length} chars")
                }.trim()
            }
            ScrapeMode.LINKS -> {
                val matches = Regex(
                    "<a[^>]+href=[\"']([^\"'#]+)[\"'][^>]*>(.*?)</a>",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
                ).findAll(html)
                matches.take(100).joinToString("\n") { m ->
                    val href = m.groupValues[1].trim()
                    val text = stripTags(m.groupValues[2]).replace(Regex("\\s+"), " ").trim()
                    if (text.isNotEmpty()) "$href\t$text" else href
                }.ifEmpty { "(no links found)" }
            }
            ScrapeMode.TEXT -> {
                stripNonContent(html)
                    .let { stripTags(it) }
                    .let { decodeHtmlEntities(it) }
                    .replace(Regex("[ \\t]+"), " ")
                    .replace(Regex("\\n{3,}"), "\n\n")
                    .trim()
            }
            ScrapeMode.MARKDOWN -> htmlToMarkdownKotlin(html)
        }
    }

    private fun stripNonContent(html: String): String {
        return html
            .replace(Regex("<script[^>]*>.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
            .replace(Regex("<style[^>]*>.*?</style>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
            .replace(Regex("<noscript[^>]*>.*?</noscript>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
    }

    private fun stripTags(input: String): String =
        input.replace(Regex("<[^>]+>"), " ")

    private fun decodeHtmlEntities(input: String): String = input
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")

    private fun htmlToMarkdownKotlin(html: String): String {
        var md = stripNonContent(html)
        md = md.replace(Regex("<h1[^>]*>(.*?)</h1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))) {
            "\n# ${stripTags(it.groupValues[1]).trim()}\n\n"
        }
        md = md.replace(Regex("<h2[^>]*>(.*?)</h2>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))) {
            "\n## ${stripTags(it.groupValues[1]).trim()}\n\n"
        }
        md = md.replace(Regex("<h3[^>]*>(.*?)</h3>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))) {
            "\n### ${stripTags(it.groupValues[1]).trim()}\n\n"
        }
        md = md.replace(Regex("<a[^>]+href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))) {
            val href = it.groupValues[1].trim()
            val label = stripTags(it.groupValues[2]).trim()
            if (label.isNotEmpty()) "[$label]($href)" else href
        }
        md = md.replace(Regex("<li[^>]*>(.*?)</li>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))) {
            "\n- ${stripTags(it.groupValues[1]).trim()}"
        }
        md = md.replace(Regex("<(p|div|section|article|br|tr)[^>]*>", RegexOption.IGNORE_CASE), "\n")
        md = stripTags(md)
        md = decodeHtmlEntities(md)
        return md
            .lines()
            .joinToString("\n") { it.trim() }
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }
}
