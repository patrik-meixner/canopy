package com.canopy.toolwindow

data class Budgeted<T>(
    val kept: List<T>,
    val omitted: Int,
    val omittedFiles: Int
)

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
