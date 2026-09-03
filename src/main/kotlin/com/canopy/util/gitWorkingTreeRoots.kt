package com.canopy.util

import java.nio.file.Path

fun gitWorkingTreeRoots(paths: Set<String>, hasGitEntry: (Path) -> Boolean): Map<String, Int> {
    val counts = LinkedHashMap<String, Int>()
    val resolved = HashMap<Path, String?>()

    for (path in paths) {
        val directory = Path.of(path).parent ?: continue
        val root = resolved.getOrPut(directory) { findRoot(directory, hasGitEntry) } ?: continue

        counts[root] = (counts[root] ?: 0) + 1
    }

    return counts
}

fun gitWorkingTreeRoots(paths: Set<String>): Map<String, Int> {
    val counts = LinkedHashMap<String, Int>()

    for (path in paths) {
        val root = GitRootCache.rootOf(directoryOf(Path.of(path))) ?: continue

        counts[root] = (counts[root] ?: 0) + 1
    }

    return counts
}

internal fun directoryOf(path: Path): Path =
    if (java.nio.file.Files.isDirectory(path)) path else path.parent ?: path

private fun findRoot(start: Path, hasGitEntry: (Path) -> Boolean): String? {
    var current: Path? = start
    while (current != null) {
        if (hasGitEntry(current.resolve(".git"))) return current.toString()

        current = current.parent
    }

    return null
}
