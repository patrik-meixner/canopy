package com.canopy.toolwindow

/**
 * One entry per path, keeping the first sighting of each.
 *
 * Selecting a range of commits asks what they did together, and a file three of them touched is
 * one file, not three rows. Commits arrive newest first, so the first sighting is the current one.
 */
fun <T> unionByPath(items: List<T>, pathOf: (T) -> String?): List<T> {
    val seen = LinkedHashMap<String, T>()

    items.forEach { item ->
        val path = pathOf(item) ?: return@forEach

        seen.putIfAbsent(path, item)
    }

    return seen.values.toList()
}
