package com.canopy.services

fun mayMoveWorktrees(path: String, isDirectory: Boolean, base: String): Boolean {
    if (!path.startsWith("$base/")) return false
    val segments = path.removePrefix("$base/").split('/')
    if (segments.dropLast(1).any { it in NEVER_DESCENDED }) return false
    val name = segments.last()

    return when {
        name == ".git" -> segments.size <= WORKTREE_SEARCH_DEPTH + 1
        isDirectory -> name !in NEVER_DESCENDED && segments.size <= WORKTREE_SEARCH_DEPTH
        else -> false
    }
}
