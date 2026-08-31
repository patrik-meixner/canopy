package com.canopy.util

import java.nio.file.Path

/**
 * The git working trees the given files live in, with how many of them landed in each.
 *
 * Walking up to the nearest `.git` covers every shape at once: the superproject, a submodule
 * (whose `.git` is a file), and a worktree of either. Deriving the roots from the paths avoids
 * having to know in advance where a session did its work.
 */
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

/** The same answer, asking the filesystem only about directories nobody has asked about before. */
fun gitWorkingTreeRoots(paths: Set<String>): Map<String, Int> {
    val counts = LinkedHashMap<String, Int>()

    for (path in paths) {
        val root = GitRootCache.rootOf(directoryOf(Path.of(path))) ?: continue

        counts[root] = (counts[root] ?: 0) + 1
    }

    return counts
}

/**
 * A shell command names directories as often as files - `cd` into a submodule, then write relative
 * paths - and taking the parent of a directory attributes its work to the repository above it.
 */
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
