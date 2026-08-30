package com.canopy.insight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityGroupingTest {

    private fun entry(tool: String, detail: String = "", at: Long = 0) = ActivityEntry(tool, detail, at)

    @Test
    fun `repeated calls to the same tool fold into one row with a count`() {
        val runs = activityRuns(listOf(entry("Bash", "git status"), entry("Bash", "git status")), writesOnly = false)

        assertEquals(1, runs.size)
        assertEquals(2, runs.single().count)
    }

    @Test
    fun `a different target starts a new row`() {
        val runs = activityRuns(listOf(entry("Edit", "/a.kt"), entry("Edit", "/b.kt")), writesOnly = false)

        assertEquals(listOf("/a.kt", "/b.kt"), runs.map { it.detail })
    }

    @Test
    fun `an edit between shell calls is not swallowed by them`() {
        val runs = activityRuns(
            listOf(entry("Bash", "ls"), entry("Edit", "/a.kt"), entry("Bash", "ls")),
            writesOnly = false
        )

        assertEquals(listOf("Bash", "Edit", "Bash"), runs.map { it.tool })
    }

    @Test
    fun `the writes filter keeps only calls that changed a file`() {
        val runs = activityRuns(listOf(entry("Bash", "ls"), entry("Write", "/a.kt")), writesOnly = true)

        assertEquals(listOf("Write"), runs.map { it.tool })
        assertTrue(runs.single().isWrite)
    }

    @Test
    fun `plumbing calls never reach the list`() {
        val runs = activityRuns(listOf(entry("ToolSearch"), entry("TaskList")), writesOnly = false)

        assertEquals(emptyList<ActivityRun>(), runs)
    }

    @Test
    fun `a run remembers when it started and when it last fired`() {
        val runs = activityRuns(listOf(entry("Bash", "ls", 100), entry("Bash", "ls", 500)), writesOnly = false)

        assertEquals(100, runs.single().firstAtMillis)
        assertEquals(500, runs.single().lastAtMillis)
    }
}
