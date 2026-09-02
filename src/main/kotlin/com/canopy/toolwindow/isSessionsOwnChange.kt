package com.canopy.toolwindow

/**
 * A file already dirty when the agent arrived is the repository's, however close to the agent it
 * sits: the date is what tells two sessions in one repository apart.
 */
fun isSessionsOwnChange(
    path: String,
    writtenPaths: Set<String>,
    ownDirectories: Collection<String>,
    startedAtMillis: Long?,
    modifiedAtMillis: (String) -> Long?
): Boolean {
    // With nothing to go on, the whole repository is the session's: an empty Changes section above
    // a full Elsewhere would be worse than not splitting at all.
    if (writtenPaths.isEmpty() && ownDirectories.isEmpty()) return true
    if (path in writtenPaths) return true
    if (ownDirectories.none { path.startsWith(it.trimEnd('/') + "/") }) return false

    val started = startedAtMillis ?: return true
    val modified = modifiedAtMillis(path) ?: return true

    return modified >= started
}
