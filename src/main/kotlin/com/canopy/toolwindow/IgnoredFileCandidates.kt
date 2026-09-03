package com.canopy.toolwindow

import com.canopy.util.ProcessHelper

object IgnoredFileCandidates {

    private const val TIMEOUT_MS = 15_000L

    fun detect(root: String): List<String> {
        val result = ProcessHelper.execWithTimeout(
            arrayOf("git", "-C", root, "ls-files", "--others", "--ignored", "--exclude-standard", "--directory"),
            TIMEOUT_MS
        )
        if (result.exitCode != 0) return emptyList()

        return configCandidates(result.output.lines())
    }
}

private const val MAX_DEPTH = 2
private const val MAX_CANDIDATES = 30

private val NOISE = Regex("""(^|/)(\.DS_Store|.*\.log|\.eslintcache|.*\.tsbuildinfo|.*\.pid)$""")

internal fun configCandidates(lines: List<String>): List<String> =
    lines.asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.endsWith("/") }
        .filter { it.count { character -> character == '/' } < MAX_DEPTH }
        .filterNot { NOISE.containsMatchIn(it) }
        .take(MAX_CANDIDATES)
        .toList()
