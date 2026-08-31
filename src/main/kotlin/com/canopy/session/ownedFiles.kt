package com.canopy.session

/**
 * Which of the changed files a session can be said to own.
 *
 * Owning one means every difference between HEAD and what is on disk is accounted for by the
 * session's own writes. A file anything else has touched makes the whole set unsafe to commit
 * unattended, so the answer is all or nothing rather than a best guess per file.
 *
 * Git and the filesystem are passed in, which is what makes this answerable without either.
 */
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
