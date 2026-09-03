package com.canopy.model

import com.canopy.toolwindow.WorktreeEntry

data class RepoWorktrees(
    val scope: RepoScope,
    val branch: String?,
    val worktrees: List<WorktreeEntry>
)
