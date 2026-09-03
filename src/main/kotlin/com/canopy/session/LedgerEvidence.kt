package com.canopy.session

data class LedgerEvidence(
    val written: Set<String>,
    val deleted: Set<String>,
    val commits: Set<String>,
    val roots: Set<String>
) {
    fun owns(path: String): Boolean = path in written || path in deleted
}
