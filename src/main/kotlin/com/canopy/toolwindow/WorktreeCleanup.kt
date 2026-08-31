package com.canopy.toolwindow

/**
 * A worktree that has nothing left to lose, and why.
 *
 * Deleting a worktree deletes its directory, so the rule is deliberately conservative: it has to be
 * clean, fully merged, and not in use. Anything else is left for a person to look at.
 */
data class CleanupCandidate(
    val path: String,
    val name: String,
    val branch: String?,
    val reason: String
)

fun cleanupCandidates(
    worktrees: List<WorktreeEntry>,
    statusOf: (String) -> WorktreeStatus?,
    sessionWorktreeNames: Set<String>
): List<CleanupCandidate> = worktrees.mapNotNull { entry ->
    val name = entry.path.trimEnd('/').substringAfterLast('/')
    if (name in sessionWorktreeNames) return@mapNotNull null

    val status = statusOf(name) ?: return@mapNotNull null
    if (status.state != WorktreeState.OK) return@mapNotNull null
    if (status.dirty) return@mapNotNull null
    if (status.ahead > 0) return@mapNotNull null

    val merged = status.mainBranch?.let { "merged into $it" } ?: "nothing unmerged"

    CleanupCandidate(entry.path, name, entry.branch, "$merged, clean, no session")
}
