package com.canopy.toolwindow

/** Matches a whole path, not just its last segment, so a directory narrows the list too. */
fun matchesFileQuery(path: String, query: String): Boolean {
    val wanted = query.trim()
    if (wanted.isEmpty()) return true

    return path.contains(wanted, ignoreCase = true)
}
