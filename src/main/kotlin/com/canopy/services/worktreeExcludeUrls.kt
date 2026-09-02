package com.canopy.services

import java.nio.file.Files
import java.nio.file.Path

private const val WORKTREE_MARKER = "/.git/worktrees/"
private const val SEARCH_DEPTH = 3

/**
 * Every git worktree under [root], as VFS urls, found by what a worktree is rather than by where
 * anyone happened to put it.
 *
 * A worktree is a second checkout of code that is already indexed once, so indexing it buys
 * nothing and costs a full copy of the repository. Nobody edits in one either: the agent does, and
 * the diff is read from git.
 *
 * A worktree's `.git` is a file naming a directory under `.git/worktrees/`; a submodule's is a
 * file too, but it names one under `.git/modules/`, and a submodule is real code somebody edits.
 * The difference between them is that one path segment.
 *
 * The tree is walked rather than asked of git or of the project model: this answers a question the
 * index asks while it is being built, so it can neither block on a process nor reach back into the
 * half-built thing to ask about itself.
 */
fun worktreeExcludeUrls(root: String): List<String> {
    val base = Path.of(root)
    if (!Files.isDirectory(base)) return emptyList()

    return Files.walk(base, SEARCH_DEPTH).use { paths ->
        paths.filter { isWorktreeCheckout(it) }
            .map { "file://${it.toAbsolutePath()}" }
            .toList()
    }
}

private fun isWorktreeCheckout(directory: Path): Boolean {
    val git = directory.resolve(".git")
    if (!Files.isRegularFile(git)) return false

    val pointer = runCatching { Files.readString(git) }.getOrNull() ?: return false

    return WORKTREE_MARKER in pointer
}
