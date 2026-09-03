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
