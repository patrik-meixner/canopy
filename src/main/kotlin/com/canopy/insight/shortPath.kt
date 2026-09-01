package com.canopy.insight

/** A path is recognised by its tail, and the head of it is the same for every file in a repository. */
fun shortPath(path: String): String {
    if (!path.startsWith("/")) return path
    val parts = path.split("/").filter { it.isNotEmpty() }

    return if (parts.size <= 2) path else "…/" + parts.takeLast(2).joinToString("/")
}
