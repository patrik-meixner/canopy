package com.canopy.toolwindow

import com.canopy.model.RepoScope

sealed interface WorktreeTreeNode {

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

    data class Session(
        val session: com.canopy.model.SessionDisplay,
        val attention: com.canopy.model.SessionAttention
    ) : WorktreeTreeNode
}
