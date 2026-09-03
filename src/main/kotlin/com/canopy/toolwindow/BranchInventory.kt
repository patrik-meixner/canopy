package com.canopy.toolwindow

import com.canopy.util.ProcessHelper

data class BranchEntry(
    val name: String,
    val upstream: String?,
    val isGone: Boolean,
    val ahead: Int,
    val behind: Int,
    val committedAtMillis: Long,
    val isCurrent: Boolean,
    val worktreePath: String? = null
)

object BranchInventory {

    private const val GIT_TIMEOUT_MS = 20_000L
    private const val FORMAT = "%(refname:short)|%(upstream:short)|%(upstream:track)|%(committerdate:unix)|%(HEAD)"

    fun list(root: String, worktrees: List<WorktreeEntry>): List<BranchEntry> {
        val output = git(root, "for-each-ref", "--format=$FORMAT", "refs/heads") ?: return emptyList()
        val holders = worktrees.filter { it.branch != null }.associate { it.branch!! to it.path }

        return parseBranches(output)
            .map { it.copy(worktreePath = holders[it.name]) }
            .sortedByDescending { it.committedAtMillis }
    }

    private fun git(root: String, vararg args: String): String? = try {
        val result = ProcessHelper.execWithTimeout(
            arrayOf("git", "-C", root, *args),
            GIT_TIMEOUT_MS,
            extraEnv = mapOf("GIT_OPTIONAL_LOCKS" to "0")
        )
        if (result.exitCode == 0) result.output else null
    } catch (_: Exception) {
        null
    }
}

private val AHEAD = Regex("""ahead (\d+)""")

private val BEHIND = Regex("""behind (\d+)""")

internal fun parseBranches(output: String): List<BranchEntry> =
    output.lineSequence()
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val parts = line.split('|')
            if (parts.size < 5) return@mapNotNull null
            val track = parts[2]

            BranchEntry(
                name = parts[0],
                upstream = parts[1].takeIf { it.isNotEmpty() },
                isGone = track.contains("gone"),
                ahead = AHEAD.find(track)?.groupValues?.get(1)?.toIntOrNull() ?: 0,
                behind = BEHIND.find(track)?.groupValues?.get(1)?.toIntOrNull() ?: 0,
                committedAtMillis = (parts[3].toLongOrNull() ?: 0L) * 1000,
                isCurrent = parts[4].trim() == "*"
            )
        }
        .toList()

internal fun branchNote(branch: BranchEntry): String {
    val parts = mutableListOf<String>()
    branch.worktreePath?.let { parts.add("in " + it.substringAfterLast('/')) }
    if (branch.ahead > 0) parts.add("↑${branch.ahead}")
    if (branch.behind > 0) parts.add("↓${branch.behind}")
    if (branch.isGone) parts.add("remote gone")
    if (branch.upstream == null && !branch.isGone) parts.add("never pushed")
    parts.add(relativeAge(branch.committedAtMillis, System.currentTimeMillis()))

    return parts.joinToString("  ")
}
