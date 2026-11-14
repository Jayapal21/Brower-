package com.example.engine

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

/**
 * Port of `crates/obscura-ssrf/src/lib.rs`.
 * Centralizes SSRF protection across literal hosts and DNS resolution checks.
 */
object SsrfGuard {

    data class ValidationResult(
        val allowed: Boolean,
        val reason: String,
    )

    fun validateUrl(urlString: String, allowPrivateNetwork: Boolean): ValidationResult {
        if (allowPrivateNetwork) {
            return ValidationResult(true, "Private network access allowed by setting")
        }
        val uri = runCatching { URI(urlString) }.getOrNull()
            ?: return ValidationResult(false, "Malformed URL")
        val scheme = uri.scheme?.lowercase().orEmpty()
        if (scheme != "http" && scheme != "https") {
            if (scheme == "about" || scheme == "data") {
                return ValidationResult(true, "Local browser scheme ($scheme)")
            }
            return ValidationResult(false, "Unsupported scheme: $scheme")
        }
        val host = uri.host?.trim()?.trimStart('[')?.trimEnd(']')
            ?: return ValidationResult(false, "Missing host in URL")

        if (host.equals("localhost", ignoreCase = true) || host.endsWith(".localhost", ignoreCase = true)) {
            return ValidationResult(false, "SSRF blocked: localhost target ($host)")
        }

        // Check literal IP or resolve DNS
        return try {
            val addresses = InetAddress.getAllByName(host)
            for (addr in addresses) {
                if (isForbiddenIp(addr)) {
                    return ValidationResult(
                        false,
                        "SSRF blocked: $host resolved to forbidden IP ${addr.hostAddress}",
                    )
                }
            }
            ValidationResult(true, "Public IP verified (${addresses.firstOrNull()?.hostAddress ?: host})")
        } catch (_: Exception) {
            // If offline or unresolvable, also check literal IP parsing directly
            val literal = runCatching { InetAddress.getByName(host) }.getOrNull()
            if (literal != null && isForbiddenIp(literal)) {
                ValidationResult(false, "SSRF blocked: forbidden literal IP ${literal.hostAddress}")
            } else {
                ValidationResult(true, "Host check passed ($host)")
            }
        }
    }

    /**
     * Quick synchronous literal-host check (used inside WebView.shouldInterceptRequest
     * when host is an IP literal or known localhost).
     */
    fun isForbiddenHostLiteral(host: String): Boolean {
        val clean = host.trim().trimStart('[').trimEnd(']').lowercase()
        if (clean == "localhost" || clean.endsWith(".localhost") || clean.endsWith(".local")) {
            return true
        }
        val isLikelyIp = clean.all { it.isDigit() || it == '.' } || clean.contains(':')
        if (!isLikelyIp) return false
        val addr = runCatching { InetAddress.getByName(clean) }.getOrNull() ?: return false
        return isForbiddenIp(addr)
    }

    /**
     * Exact port of `is_forbidden_ip` from `crates/obscura-ssrf/src/lib.rs`.
     */
    fun isForbiddenIp(ip: InetAddress): Boolean {
        return when (ip) {
            is Inet4Address -> isForbiddenIpv4(ip.address)
            is Inet6Address -> isForbiddenIpv6(ip)
            else -> false
        }
    }

    private fun isForbiddenIpv4(o: ByteArray): Boolean {
        val b0 = o[0].toInt() and 0xFF
        val b1 = o[1].toInt() and 0xFF
        val b2 = o[2].toInt() and 0xFF
        val b3 = o[3].toInt() and 0xFF

        // 0.0.0.0/8 (unspecified / this network)
        if (b0 == 0) return true
        // 127.0.0.0/8 (loopback)
        if (b0 == 127) return true
        // 10.0.0.0/8 (RFC1918)
        if (b0 == 10) return true
        // 172.16.0.0/12 (RFC1918)
        if (b0 == 172 && b1 in 16..31) return true
        // 192.168.0.0/16 (RFC1918)
        if (b0 == 192 && b1 == 168) return true
        // 169.254.0.0/16 (link-local & cloud metadata 169.254.169.254)
        if (b0 == 169 && b1 == 254) return true
        // 100.64.0.0/10 (CGNAT / RFC6598, Alibaba metadata 100.100.100.200)
        if (b0 == 100 && b1 in 64..127) return true
        // 198.18.0.0/15 (benchmarking / RFC2544)
        if (b0 == 198 && (b1 == 18 || b1 == 19)) return true
        // 192.88.99.0/24 (6to4 relay anycast / RFC7526)
        if (b0 == 192 && b1 == 88 && b2 == 99) return true
        // 192.0.2.0/24, 198.51.100.0/24, 203.0.113.0/24 (documentation)
        if (b0 == 192 && b1 == 0 && b2 == 2) return true
        if (b0 == 198 && b1 == 51 && b2 == 100) return true
        if (b0 == 203 && b1 == 0 && b2 == 113) return true
        // 192.0.0.0/24 except .9 and .10 (PCP anycast)
        if (b0 == 192 && b1 == 0 && b2 == 0 && b3 != 9 && b3 != 10) return true
        // 224.0.0.0/4 (multicast) and 240.0.0.0/4 (reserved/broadcast)
        if (b0 >= 224) return true

        return false
    }

    private fun isForbiddenIpv6(v6: Inet6Address): Boolean {
        if (v6.isLoopbackAddress ||
            v6.isAnyLocalAddress ||
            v6.isLinkLocalAddress ||
            v6.isSiteLocalAddress ||
            v6.isMulticastAddress
        ) {
            return true
        }

        val bytes = v6.address
        val s = IntArray(8) { idx ->
            ((bytes[idx * 2].toInt() and 0xFF) shl 8) or (bytes[idx * 2 + 1].toInt() and 0xFF)
        }

        // Unique local fc00::/7
        if ((s[0] and 0xFE00) == 0xFC00) return true

        // IPv4-mapped (::ffff:a.b.c.d) or IPv4-compatible (::a.b.c.d)
        if (s[0] == 0 && s[1] == 0 && s[2] == 0 && s[3] == 0 && s[4] == 0 && (s[5] == 0xFFFF || s[5] == 0)) {
            val v4 = byteArrayOf(bytes[12], bytes[13], bytes[14], bytes[15])
            return isForbiddenIpv4(v4)
        }

        // IPv4/IPv6 translation prefix 64:ff9b::/96 (RFC 6052)
        if (s[0] == 0x0064 && s[1] == 0xFF9B && s[2] == 0 && s[3] == 0 && s[4] == 0 && s[5] == 0) {
            val v4 = byteArrayOf(bytes[12], bytes[13], bytes[14], bytes[15])
            return isForbiddenIpv4(v4)
        }

        // 6to4 (2002::/16)
        if (s[0] == 0x2002) {
            val v4 = byteArrayOf(bytes[2], bytes[3], bytes[4], bytes[5])
            return isForbiddenIpv4(v4)
        }

        // Teredo (2001:0::/32)
        if (s[0] == 0x2001 && s[1] == 0x0000) {
            val server = byteArrayOf(bytes[4], bytes[5], bytes[6], bytes[7])
            val client = byteArrayOf(
                (bytes[12].toInt() xor 0xFF).toByte(),
                (bytes[13].toInt() xor 0xFF).toByte(),
                (bytes[14].toInt() xor 0xFF).toByte(),
                (bytes[15].toInt() xor 0xFF).toByte(),
            )
            return isForbiddenIpv4(server) || isForbiddenIpv4(client)
        }

        // Discard-only (100::/64), local-use NAT64 (64:ff9b:1::/48), documentation (2001:db8::/32, 3fff::/20)
        if (s[0] == 0x0100 && s[1] == 0 && s[2] == 0 && s[3] == 0) return true
        if (s[0] == 0x0064 && s[1] == 0xFF9B && s[2] == 1) return true
        if (s[0] == 0x2001 && s[1] == 0x0DB8) return true
        if (s[0] == 0x3FFF && (s[1] and 0xF000) == 0) return true

        return false
    }
}
