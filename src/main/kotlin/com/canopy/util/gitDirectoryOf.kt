package com.canopy.util

import java.nio.file.Files
import java.nio.file.Path

/** A worktree's `.git` is a file naming the real directory, not the directory itself. */
fun gitDirectoryOf(root: String): Path? {
    val git = Path.of(root, ".git")
    if (Files.isDirectory(git)) return git
    if (!Files.isRegularFile(git)) return null

    val pointer = runCatching { Files.readString(git) }.getOrNull() ?: return null
    val named = pointer.substringAfter("gitdir:", "").trim().ifEmpty { return null }

    // A submodule names its directory relative to its own .git file; a worktree names it absolutely.
    val path = git.parent.resolve(named).normalize()

    return path.takeIf { Files.isDirectory(it) }
}
