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

private fun findRoot(start: Path, hasGitEntry: (Path) -> Boolean): String? {
    var current: Path? = start
    while (current != null) {
        if (hasGitEntry(current.resolve(".git"))) return current.toString()

        current = current.parent
    }

    return null
}
