package com.canopy.toolwindow

fun isSessionsOwnChange(
    path: String,
    writtenPaths: Set<String>,
    ownDirectories: Collection<String>,
    startedAtMillis: Long?,
    modifiedAtMillis: (String) -> Long?
): Boolean {
    if (writtenPaths.isEmpty() && ownDirectories.isEmpty()) return true
    if (path in writtenPaths) return true
    if (ownDirectories.none { path.startsWith(it.trimEnd('/') + "/") }) return false

    val started = startedAtMillis ?: return true
    val modified = modifiedAtMillis(path) ?: return true

    return modified >= started
}
