package com.canopy.insight

fun shortPath(path: String): String {
    if (!path.startsWith("/")) return path
    val parts = path.split("/").filter { it.isNotEmpty() }

    return if (parts.size <= 2) path else "…/" + parts.takeLast(2).joinToString("/")
}
