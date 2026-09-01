package com.canopy.toolwindow

/** What a count of outstanding files means for what to do next, which is what colours it. */
enum class OutstandingTone { Yours, Ahead, Untracked, Clear }

data class OutstandingCount(val count: Int, val label: String, val tone: OutstandingTone)

/**
 * What is outstanding, as countable states rather than a sentence.
 *
 * The tree below says which files and in which state; this exists to answer whether anything needs
 * doing at all, without expanding four sections to find out. A sentence made that a thing to read;
 * three numbers make it a thing to glance at.
 */
fun outstandingCounts(
    uncommitted: Int,
    untracked: Int,
    unpushed: Int,
    pushed: Int
): List<OutstandingCount> {
    val outstanding = listOf(
        OutstandingCount(uncommitted, "to commit", OutstandingTone.Yours),
        OutstandingCount(unpushed, "to push", OutstandingTone.Ahead),
        OutstandingCount(untracked, "untracked", OutstandingTone.Untracked)
    ).filter { it.count > 0 }
    if (outstanding.isNotEmpty()) return outstanding

    return listOf(OutstandingCount(pushed, "pushed", OutstandingTone.Clear))
}
