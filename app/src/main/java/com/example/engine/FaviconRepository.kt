package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.example.data.ObscuraSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Fetches, decodes, and caches authentic favicons directly from each website's
 * original host (via HTML `<link rel="...icon...">` discovery and standard origin
 * paths) with memory + disk caching and SSRF guard validation.
 */
class FaviconRepository(
    context: Context,
    private val httpClient: ObscuraHttpClient,
) {
    private val cacheDir = File(context.cacheDir, "favicons").apply { mkdirs() }
    private val memoryCache = LruCache<String, Bitmap>(64)
    private val failedHosts = ConcurrentHashMap.newKeySet<String>()
    private val inFlight = ConcurrentHashMap<String, Deferred<Bitmap?>>()

    fun getCached(url: String): Bitmap? {
        val key = hostKey(url)
        if (key.isEmpty()) return null
        return memoryCache.get(key)
    }

    suspend fun getOrFetchFavicon(url: String, settings: ObscuraSettings): Bitmap? {
        val key = hostKey(url)
        if (key.isEmpty()) return null

        memoryCache.get(key)?.let { cached ->
            if (!cached.isRecycled) return cached
        }

        val diskBitmap = withContext(Dispatchers.IO) { readFromDisk(key) }
        if (diskBitmap != null) {
            memoryCache.put(key, diskBitmap)
            failedHosts.remove(key)
            return diskBitmap
        }

        if (failedHosts.contains(key)) return null

        val createdDeferred = CompletableDeferred<Bitmap?>()
        val existing = inFlight.putIfAbsent(key, createdDeferred)
        if (existing != null) {
            return existing.await()
        }

        return try {
            val fetched = withContext(Dispatchers.IO) {
                fetchFromOriginalSite(url, settings)
            }
            if (fetched != null) {
                memoryCache.put(key, fetched)
                failedHosts.remove(key)
                withContext(Dispatchers.IO) { writeToDisk(key, fetched) }
            } else {
                failedHosts.add(key)
            }
            createdDeferred.complete(fetched)
            fetched
        } catch (t: Throwable) {
            createdDeferred.complete(null)
            null
        } finally {
            inFlight.remove(key, createdDeferred)
        }
    }

    suspend fun cacheLiveBitmap(
        url: String,
        rawBitmap: Bitmap,
        persistToDisk: Boolean,
    ): Pair<String, Bitmap>? {
        val key = hostKey(url)
        if (key.isEmpty() || rawBitmap.isRecycled) return null
        if (rawBitmap.width < MIN_ICON_PX || rawBitmap.height < MIN_ICON_PX) return null

        val normalized = normalizeBitmap(rawBitmap)
        memoryCache.put(key, normalized)
        failedHosts.remove(key)
        if (persistToDisk) {
            withContext(Dispatchers.IO) { writeToDisk(key, normalized) }
        }
        return key to normalized
    }

    private fun fetchFromOriginalSite(rawUrl: String, settings: ObscuraSettings): Bitmap? {
        val normalizedUrl = httpClient.normalizeUrl(rawUrl)
        val headResult = httpClient.fetchHeadSnippet(normalizedUrl, settings)
        val baseUrl = headResult?.first ?: normalizedUrl
        val htmlHead = headResult?.second.orEmpty()

        val candidates = extractIconCandidates(htmlHead, baseUrl)
        for (candidateUrl in candidates) {
            val bytes = httpClient.fetchBinaryBytes(candidateUrl, settings, maxBytes = MAX_ICON_BYTES) ?: continue
            val decoded = decodeAndNormalize(bytes)
            if (decoded != null) {
                return decoded
            }
        }
        return null
    }

    private fun readFromDisk(hostKey: String): Bitmap? {
        val file = diskFileFor(hostKey)
        if (!file.exists()) return null
        val ageMs = System.currentTimeMillis() - file.lastModified()
        if (ageMs > DISK_TTL_MS) {
            runCatching { file.delete() }
            return null
        }
        return runCatching {
            val bmp = BitmapFactory.decodeFile(file.absolutePath)
            if (bmp != null && bmp.width >= MIN_ICON_PX && bmp.height >= MIN_ICON_PX) bmp else null
        }.getOrNull()
    }

    private fun writeToDisk(hostKey: String, bitmap: Bitmap) {
        val file = diskFileFor(hostKey)
        runCatching {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
    }

    private fun diskFileFor(hostKey: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(hostKey.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(cacheDir, "$digest.png")
    }

    private fun decodeAndNormalize(bytes: ByteArray): Bitmap? {
        val decoded = runCatching {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull() ?: return null
        if (decoded.width < MIN_ICON_PX || decoded.height < MIN_ICON_PX) {
            decoded.recycle()
            return null
        }
        return normalizeBitmap(decoded, recycleSourceIfScaled = true)
    }

    private fun normalizeBitmap(source: Bitmap, recycleSourceIfScaled: Boolean = false): Bitmap {
        val maxDim = max(source.width, source.height)
        if (maxDim <= MAX_ICON_DIM_PX) {
            return if (recycleSourceIfScaled) source else (source.copy(Bitmap.Config.ARGB_8888, false) ?: source)
        }
        val scale = MAX_ICON_DIM_PX.toFloat() / maxDim.toFloat()
        val targetW = (source.width * scale).roundToInt().coerceAtLeast(1)
        val targetH = (source.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, targetW, targetH, true)
        if (recycleSourceIfScaled && scaled !== source) {
            source.recycle()
        }
        return scaled
    }

    companion object {
        private const val MIN_ICON_PX = 8
        private const val MAX_ICON_DIM_PX = 128
        private const val MAX_ICON_BYTES = 512 * 1024
        private const val DISK_TTL_MS = 7L * 24L * 60L * 60L * 1000L

        private val LINK_TAG_REGEX = Regex("<link\\b[^>]*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val ATTR_REGEX = Regex(
            """([a-zA-Z0-9_-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+))""",
            RegexOption.IGNORE_CASE,
        )

        fun hostKey(rawUrl: String): String {
            val trimmed = rawUrl.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("about:", ignoreCase = true)) return ""
            val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
            val host = runCatching { URI(withScheme).host }.getOrNull()
                ?: trimmed.substringAfter("://").substringBefore('/').substringBefore(':')
            return host.trim().lowercase().removePrefix("www.")
        }

        internal fun extractIconCandidates(htmlHead: String, baseUrl: String): List<String> {
            val parsedUri = runCatching { URI(baseUrl) }.getOrNull()
            val scored = mutableListOf<Pair<Int, String>>()

            if (htmlHead.isNotEmpty() && parsedUri != null) {
                for (match in LINK_TAG_REGEX.findAll(htmlHead)) {
                    val attrs = parseAttributes(match.value)
                    val relTokens = attrs["rel"].orEmpty().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
                    val isTouchIcon = relTokens.any { it == "apple-touch-icon" || it == "apple-touch-icon-precomposed" }
                    val isStandardIcon = relTokens.contains("icon") || relTokens.contains("shortcut")
                    val isMaskIcon = relTokens.contains("mask-icon")
                    if ((!isTouchIcon && !isStandardIcon) || isMaskIcon) continue

                    val rawHref = attrs["href"]?.trim().orEmpty()
                    if (rawHref.isEmpty() || rawHref.startsWith("data:image/svg", ignoreCase = true)) continue

                    val mimeType = attrs["type"]?.lowercase().orEmpty()
                    if (mimeType.contains("svg")) continue

                    val resolved = runCatching { parsedUri.resolve(rawHref).toString() }.getOrNull() ?: continue
                    if (!resolved.startsWith("http://", ignoreCase = true) &&
                        !resolved.startsWith("https://", ignoreCase = true)
                    ) {
                        continue
                    }
                    val pathLower = resolved.substringBefore('?').substringBefore('#').lowercase()
                    if (pathLower.endsWith(".svg")) continue

                    val sizeScore = parseMaxSize(attrs["sizes"].orEmpty())
                    val score = when {
                        isTouchIcon -> 1000 + sizeScore.coerceIn(0, 512)
                        sizeScore >= 64 -> 800 + sizeScore.coerceIn(0, 512)
                        pathLower.endsWith(".png") || pathLower.endsWith(".webp") -> 700 + sizeScore
                        sizeScore > 0 -> 500 + sizeScore
                        else -> 400
                    }
                    scored.add(score to resolved)
                }
            }

            val ordered = LinkedHashSet<String>()
            scored.sortedByDescending { it.first }.forEach { ordered.add(it.second) }

            if (parsedUri != null && !parsedUri.host.isNullOrBlank()) {
                val scheme = parsedUri.scheme?.lowercase()?.takeIf { it == "http" || it == "https" } ?: "https"
                val portPart = if (parsedUri.port != -1) ":${parsedUri.port}" else ""
                val origin = "$scheme://${parsedUri.host}$portPart"
                ordered.add("$origin/apple-touch-icon.png")
                ordered.add("$origin/apple-touch-icon-precomposed.png")
                ordered.add("$origin/favicon-32x32.png")
                ordered.add("$origin/favicon.png")
                ordered.add("$origin/favicon.ico")
            }
            return ordered.toList()
        }

        private fun parseAttributes(tag: String): Map<String, String> {
            val map = HashMap<String, String>()
            for (m in ATTR_REGEX.findAll(tag)) {
                val key = m.groupValues[1].lowercase()
                val value = m.groupValues[2].ifEmpty { m.groupValues[3] }.ifEmpty { m.groupValues[4] }
                map[key] = value
            }
            return map
        }

        private fun parseMaxSize(sizes: String): Int {
            if (sizes.isBlank()) return 0
            return Regex("(\\d+)x(\\d+)", RegexOption.IGNORE_CASE)
                .findAll(sizes)
                .mapNotNull { it.groupValues[1].toIntOrNull() }
                .maxOrNull() ?: 0
        }
    }
}
