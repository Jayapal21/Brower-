package com.example.engine

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Port of `crates/obscura-net/src/blocklist.rs`.
 * Loads the 3,520+ domain `pgl_domains.txt` tracker blocklist from assets
 * and matches exact hosts as well as subdomains.
 */
class TrackerBlocklist private constructor(
    private val loader: () -> Set<String>,
) {
    constructor(context: Context) : this({ loadFromAssets(context.applicationContext) })

    constructor(domains: Set<String>) : this({
        domains.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toHashSet()
    })

    private val domains: Set<String> by lazy(loader)

    private companion object {
        fun loadFromAssets(context: Context): Set<String> {
            val set = HashSet<String>(4096)
            runCatching {
                context.assets.open("pgl_domains.txt").use { stream ->
                    BufferedReader(InputStreamReader(stream)).useLines { lines ->
                        lines.forEach { raw ->
                            val domain = raw.trim().lowercase()
                            if (domain.isNotEmpty() && !domain.startsWith("#")) {
                                set.add(domain)
                            }
                        }
                    }
                }
            }
            return set
        }
    }

    fun domainCount(): Int = domains.size

    /**
     * Returns the matched blocklist rule if [host] or any parent domain is in the blocklist.
     */
    fun matchedRule(host: String): String? {
        val clean = host.trim().lowercase().trimEnd('.')
        if (clean.isEmpty()) return null
        if (domains.contains(clean)) return clean

        var remaining = clean
        while (true) {
            val dot = remaining.indexOf('.')
            if (dot == -1 || dot + 1 >= remaining.length) break
            remaining = remaining.substring(dot + 1)
            if (domains.contains(remaining)) {
                return remaining
            }
        }
        return null
    }

    fun isBlocked(host: String): Boolean = matchedRule(host) != null
}
