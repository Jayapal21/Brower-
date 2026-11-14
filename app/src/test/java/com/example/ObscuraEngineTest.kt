package com.example

import com.example.data.BookmarkItem
import com.example.data.BrowserOverlayMode
import com.example.data.BrowserWorkspaceState
import com.example.data.ObscuraSettings
import com.example.data.SearchEngineOption
import com.example.data.SuggestionKind
import com.example.engine.AddressSuggestionEngine
import com.example.engine.FaviconRepository
import com.example.engine.ObscuraHttpClient
import com.example.engine.RobotsCache
import com.example.engine.SsrfGuard
import com.example.engine.TrackerBlocklist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class ObscuraEngineTest {

    @Test
    fun ssrfBlocksLoopbackPrivateMetadataAndAllowsPublic() {
        assertTrue(SsrfGuard.isForbiddenIp(InetAddress.getByName("127.0.0.1")))
        assertTrue(SsrfGuard.isForbiddenIp(InetAddress.getByName("10.0.0.5")))
        assertTrue(SsrfGuard.isForbiddenIp(InetAddress.getByName("172.16.0.1")))
        assertTrue(SsrfGuard.isForbiddenIp(InetAddress.getByName("192.168.1.1")))
        assertTrue(SsrfGuard.isForbiddenIp(InetAddress.getByName("169.254.169.254")))
        assertTrue(SsrfGuard.isForbiddenIp(InetAddress.getByName("100.64.0.1")))
        assertTrue(SsrfGuard.isForbiddenIp(InetAddress.getByName("::1")))
        assertTrue(SsrfGuard.isForbiddenIp(InetAddress.getByName("fe80::1")))

        assertFalse(SsrfGuard.isForbiddenIp(InetAddress.getByName("93.184.216.34")))
        assertFalse(SsrfGuard.isForbiddenIp(InetAddress.getByName("1.1.1.1")))
    }

    @Test
    fun robotsCacheFollowsRfc9309LongestMatchAndAllowTieBreak() {
        val cache = RobotsCache()
        val body = """
            User-agent: *
            Allow: /p
            Disallow: /p/private
            Disallow: /tie
            Allow: /tie
        """.trimIndent()
        cache.parseAndStore("https://paths.test", body, "Obscura")

        assertTrue(cache.isAllowed("https://paths.test", "/p"))
        assertFalse(cache.isAllowed("https://paths.test", "/p/private/secret"))
        assertTrue(cache.isAllowed("https://paths.test", "/tie"))
    }

    @Test
    fun addressSuggestionEngineRoutesUrlsAndSearches() {
        assertEquals(
            "https://example.com",
            AddressSuggestionEngine.resolveInputToUrl("example.com", SearchEngineOption.DUCKDUCKGO),
        )
        assertEquals(
            "https://duckduckgo.com/?q=android+stealth",
            AddressSuggestionEngine.resolveInputToUrl("android stealth", SearchEngineOption.DUCKDUCKGO),
        )

        val suggestions = AddressSuggestionEngine.buildSuggestions(
            query = "exam",
            bookmarks = listOf(BookmarkItem("1", "Example", "https://example.com")),
            history = emptyList(),
            shortcuts = emptyList(),
            searchEngine = SearchEngineOption.GOOGLE,
        )
        assertTrue(suggestions.any { it.kind == SuggestionKind.BOOKMARK })
        assertTrue(suggestions.any { it.kind == SuggestionKind.WEB_SEARCH })
    }

    @Test
    fun overlayModeEnforcesMutualExclusivity() {
        val initial = BrowserWorkspaceState()
        assertEquals(BrowserOverlayMode.None, initial.overlayMode)
        assertFalse(initial.isAddressFocused)
        assertFalse(initial.isTabSwitcherOpen)
        assertFalse(initial.isMenuOpen)
        assertFalse(initial.isFindInPageOpen)
        assertNull(initial.screenshotBitmap)

        val addressFocused = initial.copy(overlayMode = BrowserOverlayMode.AddressFocus)
        assertTrue(addressFocused.isAddressFocused)
        assertFalse(addressFocused.isTabSwitcherOpen)
        assertFalse(addressFocused.isMenuOpen)
        assertFalse(addressFocused.isFindInPageOpen)

        val tabSwitcher = addressFocused.copy(overlayMode = BrowserOverlayMode.TabSwitcher)
        assertFalse(tabSwitcher.isAddressFocused)
        assertTrue(tabSwitcher.isTabSwitcherOpen)
        assertFalse(tabSwitcher.isMenuOpen)
        assertFalse(tabSwitcher.isFindInPageOpen)

        val findInPage = tabSwitcher.copy(overlayMode = BrowserOverlayMode.FindInPage)
        assertFalse(findInPage.isTabSwitcherOpen)
        assertTrue(findInPage.isFindInPageOpen)
    }

    @Test
    fun httpClientReusesSharedConnectionPoolAndDispatcher() {
        val blocklist = TrackerBlocklist(setOf("tracker.example.com"))
        val http = ObscuraHttpClient(blocklist, RobotsCache())

        val clientA = http.configuredClient(ObscuraSettings(timeoutSeconds = 15))
        val clientB = http.configuredClient(ObscuraSettings(timeoutSeconds = 45, allowPrivateNetwork = true))

        assertSame(clientA.connectionPool, clientB.connectionPool)
        assertSame(clientA.dispatcher, clientB.dispatcher)
        assertEquals(15_000, clientA.connectTimeoutMillis)
        assertEquals(45_000, clientB.connectTimeoutMillis)
    }

    @Test
    fun trackerBlocklistMatchesExactAndSubdomainsOnly() {
        val blocklist = TrackerBlocklist(setOf("doubleclick.net", "metrics.example.org"))

        assertEquals("doubleclick.net", blocklist.matchedRule("doubleclick.net"))
        assertEquals("doubleclick.net", blocklist.matchedRule("ad.doubleclick.net"))
        assertEquals("metrics.example.org", blocklist.matchedRule("SUB.Metrics.Example.ORG."))
        assertNull(blocklist.matchedRule("notdoubleclick.net"))
        assertNull(blocklist.matchedRule("example.org"))
    }

    @Test
    fun faviconRepositoryExtractsOriginalWebsiteIconCandidates() {
        assertEquals("en.wikipedia.org", FaviconRepository.hostKey("https://en.wikipedia.org/wiki/Main_Page"))
        assertEquals("github.com", FaviconRepository.hostKey("https://www.github.com/explore"))

        val htmlHead = """
            <html>
            <head>
                <link rel="mask-icon" href="/safari-pinned-tab.svg" color="#000000">
                <link rel="icon" type="image/svg+xml" href="/logo.svg">
                <link rel="icon" type="image/png" sizes="32x32" href="/assets/icon-32.png">
                <link rel="apple-touch-icon" sizes="180x180" href="/apple-touch-icon.png">
                <link rel="shortcut icon" href="https://cdn.example.org/favicon.ico">
            </head>
            </html>
        """.trimIndent()

        val candidates = FaviconRepository.extractIconCandidates(htmlHead, "https://example.org/docs")
        assertTrue(candidates.none { it.endsWith(".svg") })
        assertEquals("https://example.org/apple-touch-icon.png", candidates.first())
        assertTrue(candidates.contains("https://example.org/assets/icon-32.png"))
        assertTrue(candidates.contains("https://cdn.example.org/favicon.ico"))
        assertTrue(candidates.contains("https://example.org/favicon.ico"))
    }
}
