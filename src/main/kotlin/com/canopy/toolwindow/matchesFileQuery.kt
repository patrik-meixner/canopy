package com.canopy.toolwindow

fun matchesFileQuery(path: String, query: String): Boolean {
    val wanted = query.trim()
    if (wanted.isEmpty()) return true

    return path.contains(wanted, ignoreCase = true)
}
