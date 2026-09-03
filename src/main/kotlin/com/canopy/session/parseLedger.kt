package com.canopy.session

private const val FIELDS = 4
private const val KIND = 1
private const val ROOT = 2
private const val VALUE = 3

/** One record per line: `millis TAB kind TAB root TAB path-or-sha`, kind being W, D or C. Anything else is skipped. */
fun parseLedger(lines: Sequence<String>): LedgerEvidence {
    val written = LinkedHashSet<String>()
    val deleted = LinkedHashSet<String>()
    val commits = LinkedHashSet<String>()
    val roots = LinkedHashSet<String>()

    for (line in lines) {
        val fields = line.split('\t')
        if (fields.size != FIELDS) continue

        val target = when (fields[KIND]) {
            "W" -> written
            "D" -> deleted
            "C" -> commits
            else -> continue
        }
        target += fields[VALUE]
        roots += fields[ROOT]
    }

    return LedgerEvidence(written, deleted, commits, roots)
}
