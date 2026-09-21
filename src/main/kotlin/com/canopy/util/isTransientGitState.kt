package com.canopy.util

private val MID_OPERATION = setOf(
    "MERGE_HEAD",
    "REBASE_HEAD",
    "CHERRY_PICK_HEAD",
    "REVERT_HEAD",
    "rebase-merge",
    "rebase-apply"
)

/**
 * A merge, rebase, cherry-pick or revert fills the working tree with changes the session never
 * made, and attributing those to it is worse than showing nothing.
 */
fun isTransientGitState(gitDirEntries: Set<String>): Boolean = gitDirEntries.any { it in MID_OPERATION }
