package com.canopy.session

fun ownedFiles(
    changed: Set<String>,
    writes: List<FileWrite>,
    headOf: (String) -> String,
    currentOf: (String) -> String?,
): List<String> {
    val byPath = writes.groupBy { it.path }
    val candidates = changed.filter { it in byPath }
    if (candidates.isEmpty()) return emptyList()

    val owned = candidates.filter { path ->
        val current = currentOf(path) ?: return emptyList()

        writesAccountFor(headOf(path), current, byPath.getValue(path))
    }

    return if (owned.size == candidates.size) owned else emptyList()
}
