package io.github.crashy.crashlogs

import io.github.crashy.utils.getResource

object Blacklist {
    /**
     * Upstream kept this in a gitignored secrets file and crashed when it was missing. Self-hosted
     * deployments get the same list through CRASHY_BLACKLIST (newline or comma separated) instead.
     */
    private val items: Set<String> =
        (getResource("/secrets/blacklist.txt") ?: System.getenv("CRASHY_BLACKLIST") ?: "")
            .split('\n', ',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toHashSet()

    fun isBlacklisted(ip: String): Boolean {
        return ip in items
    }
}
