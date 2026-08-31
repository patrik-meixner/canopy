package com.canopy.toolwindow

/**
 * Names for a set of worktrees started from one prompt.
 *
 * Trying three approaches to the same problem means three worktrees that have to be told apart
 * afterwards, so the names carry the base rather than a counter alone.
 */
fun fanOutNames(base: String, count: Int, taken: Set<String>): List<String> {
    val stem = slug(base)
    val names = mutableListOf<String>()
    var index = 1

    while (names.size < count) {
        val candidate = "$stem-$index"
        if (candidate !in taken && candidate !in names) names.add(candidate)
        index++
        if (index > count + taken.size + 1) break
    }

    return names
}
