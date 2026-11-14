package com.example.engine

/**
 * Port of `crates/obscura-net/src/robots.rs` (RFC 9309 compliant).
 */
class RobotsCache {

    companion object {
        private const val MAX_ROBOTS_ENTRIES = 4096
        private const val MAX_ROBOTS_BODY_BYTES = 500 * 1024
    }

    private data class RobotsRules(
        val disallowed: List<String>,
        val allowed: List<String>,
    )

    private val lock = Any()
    private val rulesMap = HashMap<String, RobotsRules>()
    private val insertionOrder = ArrayDeque<String>()

    fun size(): Int = synchronized(lock) { rulesMap.size }

    fun contains(origin: String): Boolean = synchronized(lock) { rulesMap.containsKey(origin) }

    fun clear() = synchronized(lock) {
        rulesMap.clear()
        insertionOrder.clear()
    }

    fun parseAndStore(origin: String, body: String, ourAgent: String) {
        val truncated = truncateBody(body)
        val rules = parseRobotsTxt(truncated, ourAgent)
        synchronized(lock) {
            val existed = rulesMap.put(origin, rules) != null
            if (!existed) {
                insertionOrder.addLast(origin)
            }
            while (rulesMap.size > MAX_ROBOTS_ENTRIES) {
                val oldest = insertionOrder.removeFirstOrNull() ?: break
                rulesMap.remove(oldest)
            }
        }
    }

    fun isAllowed(origin: String, path: String): Boolean {
        val rules = synchronized(lock) { rulesMap[origin] } ?: return true

        // RFC 9309 2.2.2: longest match wins; Allow wins on tie.
        var bestLength = -1
        var bestAllow = true

        for (pattern in rules.allowed) {
            if (pathMatches(path, pattern) && pattern.length >= bestLength) {
                bestLength = pattern.length
                bestAllow = true
            }
        }
        for (pattern in rules.disallowed) {
            if (pathMatches(path, pattern) &&
                (pattern.length > bestLength || (pattern.length == bestLength && !bestAllow))
            ) {
                bestLength = pattern.length
                bestAllow = false
            }
        }
        return bestAllow
    }

    private fun truncateBody(body: String): String {
        val bytes = body.toByteArray(Charsets.UTF_8)
        if (bytes.size <= MAX_ROBOTS_BODY_BYTES) return body
        val head = String(bytes, 0, MAX_ROBOTS_BODY_BYTES, Charsets.UTF_8)
        val lastNewline = head.lastIndexOf('\n')
        return if (lastNewline >= 0) head.substring(0, lastNewline) else head
    }

    private fun parseRobotsTxt(body: String, ourAgent: String): RobotsRules {
        val ourTokens = ourAgent
            .split(' ', '/', '(', ')', ';', ',')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }

        class Group(
            val agents: MutableList<String> = mutableListOf(),
            val disallowed: MutableList<String> = mutableListOf(),
            val allowed: MutableList<String> = mutableListOf(),
            var rulesStarted: Boolean = false,
        )

        val groups = mutableListOf<Group>()
        body.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith('#')) return@forEach
            val colonIdx = line.indexOf(':')
            if (colonIdx == -1) return@forEach
            val key = line.substring(0, colonIdx).trim().lowercase()
            val value = line.substring(colonIdx + 1).trim()

            when (key) {
                "user-agent" -> {
                    if (groups.isEmpty() || groups.last().rulesStarted) {
                        groups.add(Group())
                    }
                    groups.last().agents.add(value.lowercase())
                }
                "allow", "disallow" -> {
                    val group = groups.lastOrNull() ?: return@forEach
                    group.rulesStarted = true
                    if (value.isEmpty()) return@forEach
                    if (key == "allow") {
                        group.allowed.add(value)
                    } else {
                        group.disallowed.add(value)
                    }
                }
            }
        }

        fun matchesSpecific(group: Group): Boolean =
            group.agents.any { agent -> agent != "*" && ourTokens.any { it == agent } }

        val hasSpecific = groups.any { matchesSpecific(it) }
        val disallowed = mutableListOf<String>()
        val allowed = mutableListOf<String>()

        groups.filter { group ->
            if (hasSpecific) matchesSpecific(group) else group.agents.any { it == "*" }
        }.forEach { group ->
            disallowed.addAll(group.disallowed)
            allowed.addAll(group.allowed)
        }

        return RobotsRules(disallowed = disallowed, allowed = allowed)
    }

    private fun pathMatches(path: String, pattern: String): Boolean {
        return when {
            pattern.endsWith("*") -> path.startsWith(pattern.dropLast(1))
            pattern.endsWith("$") -> path == pattern.dropLast(1)
            else -> path.startsWith(pattern)
        }
    }
}
