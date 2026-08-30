package com.canopy.model

/**
 * One git repository the project spans: the superproject itself, or an initialized submodule.
 * [gitCommonDir] is the shared git directory, which is what identifies a repo across its worktrees.
 */
data class RepoScope(
    val label: String,
    val root: String,
    val gitCommonDir: String,
    val isSubmodule: Boolean
)
