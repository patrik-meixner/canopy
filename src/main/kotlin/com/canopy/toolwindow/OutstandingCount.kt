package com.canopy.toolwindow

enum class OutstandingTone { Yours, Ahead, Untracked, Clear }

data class OutstandingCount(val count: Int, val label: String, val tone: OutstandingTone)

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
