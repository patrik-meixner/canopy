package com.canopy.toolwindow

import com.canopy.model.RepoScope

sealed interface WorktreeTreeNode {

    /** Worktrees and branches are different questions, so they are different roots. */
    data class Section(val title: String, val count: Int) : WorktreeTreeNode {
        override fun toString(): String = title
    }

    data class Repo(
        val scope: RepoScope,
        val branch: String?,
        val worktreeCount: Int
    ) : WorktreeTreeNode

    data class Worktree(
        val scope: RepoScope,
        val entry: WorktreeEntry,
        val status: WorktreeStatus?
    ) : WorktreeTreeNode

    data class Branch(
        val scope: RepoScope,
        val entry: BranchEntry
    ) : WorktreeTreeNode

    data class Session(
        val session: com.canopy.model.SessionDisplay,
        val attention: com.canopy.model.SessionAttention
    ) : WorktreeTreeNode
}
