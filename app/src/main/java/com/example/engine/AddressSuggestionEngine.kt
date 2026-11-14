package com.example.engine

import com.example.data.AddressSuggestion
import com.example.data.BookmarkItem
import com.example.data.HistoryItem
import com.example.data.SearchEngineOption
import com.example.data.SuggestionKind
import com.example.data.WebsiteShortcut
import java.net.URLEncoder

object AddressSuggestionEngine {

    fun isLikelyUrl(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed.contains(' ')) return false
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("about:", ignoreCase = true)
        ) {
            return true
        }
        if (trimmed.equals("localhost", ignoreCase = true) || trimmed.startsWith("localhost:", ignoreCase = true)) {
            return true
        }
        return trimmed.contains('.') && !trimmed.startsWith('.') && !trimmed.endsWith('.')
    }

    fun resolveInputToUrl(input: String, searchEngine: SearchEngineOption): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed.equals("about:home", ignoreCase = true)) {
            return "about:home"
        }
        if (isLikelyUrl(trimmed)) {
            return if (trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) ||
                trimmed.startsWith("about:", ignoreCase = true)
            ) {
                trimmed
            } else {
                "https://$trimmed"
            }
        }
        val encoded = runCatching { URLEncoder.encode(trimmed, "UTF-8") }.getOrDefault(trimmed)
        return "${searchEngine.searchUrlTemplate}$encoded"
    }

    fun buildSuggestions(
        query: String,
        bookmarks: List<BookmarkItem>,
        history: List<HistoryItem>,
        shortcuts: List<WebsiteShortcut>,
        searchEngine: SearchEngineOption,
    ): List<AddressSuggestion> {
        val q = query.trim().lowercase()
        val results = mutableListOf<AddressSuggestion>()
        val seenUrls = HashSet<String>()

        if (q.isEmpty()) {
            bookmarks.take(4).forEach { bm ->
                if (seenUrls.add(bm.url)) {
                    results.add(AddressSuggestion(bm.title, bm.url, bm.url, SuggestionKind.BOOKMARK))
                }
            }
            history.take(4).forEach { h ->
                if (seenUrls.add(h.url)) {
                    results.add(AddressSuggestion(h.title.ifEmpty { h.url }, h.url, h.url, SuggestionKind.HISTORY))
                }
            }
            return results
        }

        if (isLikelyUrl(query)) {
            val resolved = resolveInputToUrl(query, searchEngine)
            if (seenUrls.add(resolved)) {
                results.add(AddressSuggestion("Go to $query", resolved, resolved, SuggestionKind.DIRECT_URL))
            }
        }

        val searchUrl = "${searchEngine.searchUrlTemplate}${runCatching { URLEncoder.encode(query.trim(), "UTF-8") }.getOrDefault(query.trim())}"
        results.add(
            AddressSuggestion(
                title = "Search \"${query.trim()}\"",
                subtitle = "via ${searchEngine.label}",
                targetUrl = searchUrl,
                kind = SuggestionKind.WEB_SEARCH,
            ),
        )

        bookmarks.filter {
            it.title.lowercase().contains(q) || it.url.lowercase().contains(q)
        }.take(4).forEach { bm ->
            if (seenUrls.add(bm.url)) {
                results.add(AddressSuggestion(bm.title, bm.url, bm.url, SuggestionKind.BOOKMARK))
            }
        }

        shortcuts.filter {
            it.title.lowercase().contains(q) || it.url.lowercase().contains(q)
        }.take(3).forEach { sc ->
            if (seenUrls.add(sc.url)) {
                results.add(AddressSuggestion(sc.title, sc.url, sc.url, SuggestionKind.SHORTCUT))
            }
        }

        history.filter {
            it.title.lowercase().contains(q) || it.url.lowercase().contains(q)
        }.take(5).forEach { h ->
            if (seenUrls.add(h.url)) {
                results.add(AddressSuggestion(h.title.ifEmpty { h.url }, h.url, h.url, SuggestionKind.HISTORY))
            }
        }

        return results
    }
}
