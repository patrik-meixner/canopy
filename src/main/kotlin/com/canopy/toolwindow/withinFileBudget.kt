package com.canopy.toolwindow

data class Budgeted<T>(
    val kept: List<T>,
    val omitted: Int,
    val omittedFiles: Int
)

/**
 * As many of the newest commits as fit a file budget.
 *
 * A resumed session can have pushed thousands of files, and every one of them becomes a node the
 * tree builds on the event thread before anything is drawn. The newest commits are the ones being
 * reviewed; the rest are counted rather than listed.
 */
fun <T> withinFileBudget(commits: List<T>, budget: Int, filesIn: (T) -> Int): Budgeted<T> {
    val kept = mutableListOf<T>()
    var files = 0

    for (entry in commits) {
        if (files >= budget) break

        kept.add(entry)
        files += filesIn(entry)
    }

    val omitted = commits.drop(kept.size)

    return Budgeted(kept, omitted.size, omitted.sumOf(filesIn))
}
