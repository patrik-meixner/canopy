package com.canopy.toolwindow

/**
 * Whether an uncommitted file is this session's work rather than the repository's.
 *
 * Two signals, because neither is enough alone. The written paths are exact but incomplete: an
 * agent edits through the shell as often as through its tools, and `sed` leaves no record. The
 * working directory catches those, and it is what separates two agents in one repository - which
 * the repository-wide diff they both read cannot.
 */
fun isSessionsOwnChange(path: String, writtenPaths: Set<String>, workingDirectory: String?): Boolean {
    // With nothing to go on, the whole repository is the session's: an empty Changes section above
    // a full Elsewhere would be worse than not splitting at all.
    if (writtenPaths.isEmpty() && workingDirectory.isNullOrBlank()) return true
    if (path in writtenPaths) return true
    if (workingDirectory.isNullOrBlank()) return false

    return path.startsWith(workingDirectory.trimEnd('/') + "/")
}
