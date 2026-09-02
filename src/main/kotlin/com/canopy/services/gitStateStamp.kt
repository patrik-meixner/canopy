package com.canopy.services

import java.nio.file.Files
import java.nio.file.Path

private val GIT_STATE_FILES = listOf("HEAD", "index", "FETCH_HEAD")

/**
 * When a repository's git state last moved: a commit, a push, a checkout, a staged file.
 *
 * The VFS is no help here. It learns of a write by an outside process when the IDE window is
 * activated, and the process writing here is an agent in a terminal inside that window, so the
 * frame never deactivates and the event never comes: a commit stayed invisible until a restart.
 * Three stat calls per repository ask git's own files instead.
 */
fun gitStateStamp(root: String): Long {
    val git = Path.of(root, ".git")
    val directory = if (Files.isDirectory(git)) git else worktreeGitDirectory(git) ?: return 0L

    return GIT_STATE_FILES.maxOf { name -> lastModified(directory.resolve(name)) }
}

/** A worktree's .git is a file naming the real directory, which is where its refs actually move. */
private fun worktreeGitDirectory(git: Path): Path? {
    if (!Files.isRegularFile(git)) return null

    val pointer = runCatching { Files.readString(git) }.getOrNull() ?: return null
    val path = pointer.substringAfter("gitdir:", "").trim().ifEmpty { return null }

    return Path.of(path).takeIf { Files.isDirectory(it) }
}

private fun lastModified(path: Path): Long =
    runCatching { Files.getLastModifiedTime(path).toMillis() }.getOrDefault(0L)
