package com.example

import com.example.engine.RobotsCache
import com.example.engine.SsrfGuard
import org.junit.Assert.assertFalse
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
    fun robotsCacheSpecificGroupOverridesWildcard() {
        val cache = RobotsCache()
        val body = """
            User-agent: *
            Disallow: /

            User-agent: Obscura
            Disallow: /only
        """.trimIndent()
        cache.parseAndStore("https://specific.test", body, "Obscura/1.0")

        assertTrue(cache.isAllowed("https://specific.test", "/elsewhere"))
        assertFalse(cache.isAllowed("https://specific.test", "/only"))
    }
}
