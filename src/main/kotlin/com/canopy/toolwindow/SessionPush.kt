package com.canopy.toolwindow

import com.canopy.util.ProcessHelper

data class PushOutcome(val root: String, val ok: Boolean, val detail: String, val reviewUrl: String?)

object SessionPush {

    private const val GIT_TIMEOUT_MS = 120_000L

    fun push(roots: List<String>, superproject: String?): List<PushOutcome> =
        pushOrder(roots, superproject).map(::pushOne)

    private fun pushOne(root: String): PushOutcome {
        val branch = git(root, "rev-parse", "--abbrev-ref", "HEAD").second.trim()
        val (ok, output) = git(root, "push", "--set-upstream", "origin", branch)

        return PushOutcome(root, ok, output, reviewUrl(output))
    }

    private fun git(root: String, vararg args: String): Pair<Boolean, String> = try {
        val result = ProcessHelper.execWithTimeout(arrayOf("git", "-C", root, *args), GIT_TIMEOUT_MS)
        (result.exitCode == 0) to result.output
    } catch (e: Exception) {
        false to (e.message ?: e.toString())
    }
}

internal fun pushOrder(roots: List<String>, superproject: String?): List<String> =
    roots.filter { SessionCommit.isInside(it, superproject) } +
        roots.filterNot { SessionCommit.isInside(it, superproject) }

private val REVIEW_URL = Regex("""https?://\S*(?:merge_requests|pull)\S*""")

internal fun reviewUrl(pushOutput: String): String? =
    REVIEW_URL.find(pushOutput)?.value?.trimEnd('.', ',')
