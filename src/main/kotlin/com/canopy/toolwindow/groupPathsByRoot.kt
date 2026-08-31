package com.canopy.toolwindow

import java.nio.file.Path

/**
 * Which repository each selected file belongs to, deepest root first.
 *
 * A submodule sits inside its superproject, so a path matches both and only the longest match is
 * the repository that actually tracks the file.
 */
fun groupPathsByRoot(paths: Collection<String>, roots: Collection<String>): Map<String, List<String>> {
    val deepestFirst = roots.map { Path.of(it).normalize().toAbsolutePath() }.sortedByDescending { it.nameCount }

    return paths.mapNotNull { path ->
        val file = Path.of(path).normalize().toAbsolutePath()
        deepestFirst.firstOrNull { file.startsWith(it) }?.let { it.toString() to path }
    }.groupBy({ it.first }, { it.second })
}
