package com.canopy.services

import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

private const val WORKTREE_MARKER = "/worktrees/"
private const val SEARCH_DEPTH = 4

private val NEVER_DESCENDED = setOf("node_modules", "vendor", ".git", "build", "dist", "target", ".gradle", ".idea")

/**
 * Every git worktree under [root], as VFS urls, found by what a worktree is rather than by where
 * anyone happened to put it.
 *
 * A worktree's `.git` is a file naming a directory with a `worktrees` segment in it: `.git/
 * worktrees/x` for the top-level repository, `.git/modules/<submodule>/worktrees/x` for a
 * worktree of a submodule. A submodule's own checkout is a file too, but it names `.git/modules/
 * <submodule>` with no such segment - and a submodule is real code somebody edits.
 *
 * A visitor rather than `Files.walk`, because only a visitor can skip a subtree or shrug off a broken
 * symlink without a stack trace.
 */
fun worktreeExcludeUrls(root: String): List<String> {
    val base = Path.of(root)
    if (!Files.isDirectory(base)) return emptyList()

    val found = ArrayList<String>()
    Files.walkFileTree(base, emptySet(), SEARCH_DEPTH, object : SimpleFileVisitor<Path>() {
        override fun preVisitDirectory(directory: Path, attributes: BasicFileAttributes): FileVisitResult {
            if (directory != base && directory.fileName.toString() in NEVER_DESCENDED) return FileVisitResult.SKIP_SUBTREE
            if (!isWorktreeCheckout(directory)) return FileVisitResult.CONTINUE

            found.add("file://${directory.toAbsolutePath()}")
            return FileVisitResult.SKIP_SUBTREE
        }

        override fun visitFileFailed(file: Path, exception: IOException): FileVisitResult = FileVisitResult.CONTINUE
    })

    return found
}

private fun isWorktreeCheckout(directory: Path): Boolean {
    val git = directory.resolve(".git")
    if (!Files.isRegularFile(git)) return false

    val pointer = runCatching { Files.readString(git) }.getOrNull() ?: return false

    return WORKTREE_MARKER in pointer
}
