package com.canopy.insight

/**
 * A run of calls the agent made to the same tool, folded into one row.
 *
 * A flat call log is the terminal again, only worse: twelve Bash rows say nothing that "Bash ×12"
 * does not, and they bury the one Edit between them.
 */
data class ActivityRun(
    val tool: String,
    val count: Int,
    val detail: String,
    val firstAtMillis: Long,
    val lastAtMillis: Long,
    val isWrite: Boolean
)

private val WRITE_TOOLS = setOf("Edit", "Write", "MultiEdit", "NotebookEdit")

private val NOISE_TOOLS = setOf("ToolSearch", "TaskGet", "TaskList", "TaskOutput")

fun activityRuns(entries: List<ActivityEntry>, writesOnly: Boolean): List<ActivityRun> {
    val relevant = entries.filterNot { it.tool in NOISE_TOOLS }.filter { !writesOnly || isWrite(it.tool) }
    val runs = mutableListOf<ActivityRun>()

    for (entry in relevant) {
        val last = runs.lastOrNull()
        if (last != null && last.tool == entry.tool && last.detail == entry.detail) {
            runs[runs.lastIndex] = last.copy(count = last.count + 1, lastAtMillis = entry.atMillis)
            continue
        }
        if (last != null && last.tool == entry.tool && sameShellVerb(last.detail, entry.detail)) {
            runs[runs.lastIndex] = last.copy(count = last.count + 1, lastAtMillis = entry.atMillis)
            continue
        }
        runs.add(ActivityRun(entry.tool, 1, entry.detail, entry.atMillis, entry.atMillis, isWrite(entry.tool)))
    }

    return runs
}

internal fun isWrite(tool: String): Boolean = tool in WRITE_TOOLS

/** Twenty greps in a row are one activity, whatever each of them was looking for. */
private fun sameShellVerb(first: String, second: String): Boolean =
    second.isEmpty() || summarizeCommand(first).verb == summarizeCommand(second).verb
