package com.canopy.model

/** Why a worktree directory shows up as orphaned, so the list can say something actionable. */
sealed interface OrphanReason {

    /** Git tracks it normally; it simply has no Claude session. */
    data object NoSession : OrphanReason

    /** Nothing left inside, so deleting it is free. */
    data object EmptyDirectory : OrphanReason

    /**
     * No `.git` at all, but files remain. Typically build-tool output (`.gradle`, `node_modules`)
     * recreated after `git worktree remove` deleted the tracked files, leaving the directory behind.
     */
    data class LeftoverFiles(val names: List<String>) : OrphanReason

    /** A worktree whose git directory is gone, so git cannot resolve it any more. */
    data class MissingGitDir(val gitDir: String) : OrphanReason

    /** Registered, but to a different repository than the one whose tree it sits in. */
    data class OwnedByOtherRepo(val commonDir: String) : OrphanReason
}
