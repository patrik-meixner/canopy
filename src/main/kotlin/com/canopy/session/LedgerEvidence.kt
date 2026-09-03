package com.canopy.session

/** What a session's hooks saw it change: files by absolute path, commits by sha, repositories by root. */
data class LedgerEvidence(
    val written: Set<String>,
    val deleted: Set<String>,
    val commits: Set<String>,
    val roots: Set<String>
) {
    fun owns(path: String): Boolean = path in written || path in deleted
}
