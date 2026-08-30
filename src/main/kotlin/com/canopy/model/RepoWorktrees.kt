package com.canopy.model

import com.canopy.toolwindow.WorktreeEntry

/** One repo scope and its worktrees, as one row group in the Worktrees tree. */
data class RepoWorktrees(
    val scope: RepoScope,
    val branch: String?,
    val worktrees: List<WorktreeEntry>
)
