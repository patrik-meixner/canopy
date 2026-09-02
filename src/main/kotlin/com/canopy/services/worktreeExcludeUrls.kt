package com.canopy.services

import java.nio.file.Files
import java.nio.file.Path

private const val CLAUDE_DIRECTORY = ".claude"
private const val WORKTREES_DIRECTORY = "worktrees"
private const val SUBMODULE_DEPTH = 3

/**
 * Every worktree directory under [root], as VFS urls.
 *
 * A worktree is a second checkout of code that is already indexed once, so indexing it buys
 * nothing and costs a full copy of the repository. Nobody edits in one either: the agent does,
 * and the diff is read from git.
 *
 * The tree is walked rather than asked of the project model, because this answers a question the
 * index asks while it is being built and reaching back into it would be asking the half-built
 * thing about itself.
 */
fun worktreeExcludeUrls(root: String): List<String> {
    val base = Path.of(root)
    if (!Files.isDirectory(base)) return emptyList()

    return Files.walk(base, SUBMODULE_DEPTH).use { paths ->
        paths.filter { it.fileName?.toString() == CLAUDE_DIRECTORY && Files.isDirectory(it) }
            .map { it.resolve(WORKTREES_DIRECTORY) }
            .filter { Files.isDirectory(it) }
            .map { "file://${it.toAbsolutePath()}" }
            .toList()
    }
}
