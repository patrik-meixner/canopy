package com.canopy.insight

import org.junit.Assert.assertEquals
import org.junit.Test

class PlanSummaryTest {

    private fun task(status: TaskStatus) = PlannedTask("1", "s", "", "", status, emptyList())

    @Test
    fun `the summary counts what is done out of the whole list`() {
        val tasks = listOf(task(TaskStatus.COMPLETED), task(TaskStatus.PENDING), task(TaskStatus.PENDING))

        assertEquals("1 of 3 done", summaryOf(tasks))
    }

    @Test
    fun `a running task is called out`() {
        val tasks = listOf(task(TaskStatus.COMPLETED), task(TaskStatus.IN_PROGRESS))

        assertEquals("1 of 2 done, 1 in progress", summaryOf(tasks))
    }

    @Test
    fun `an empty plan says nothing rather than zero of zero`() {
        assertEquals("", summaryOf(emptyList()))
    }
}
