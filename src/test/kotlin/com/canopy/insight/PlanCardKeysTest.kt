package com.canopy.insight

import kotlin.test.Test
import kotlin.test.assertNotEquals

class PlanCardKeysTest {

    private fun task(status: TaskStatus) = PlannedTask(
        id = "1",
        subject = "Fix the build",
        description = "",
        activeForm = "Fixing the build",
        status = status,
        blockedBy = emptyList()
    )

    @Test
    fun `a task that started running is a different card`() {
        assertNotEquals(
            planCardKeys(listOf(task(TaskStatus.PENDING))),
            planCardKeys(listOf(task(TaskStatus.IN_PROGRESS)))
        )
    }

    @Test
    fun `a task that finished is a different card`() {
        assertNotEquals(
            planCardKeys(listOf(task(TaskStatus.IN_PROGRESS))),
            planCardKeys(listOf(task(TaskStatus.COMPLETED)))
        )
    }

    @Test
    fun `a reworded task is a different card`() {
        assertNotEquals(
            planCardKeys(listOf(task(TaskStatus.PENDING))),
            planCardKeys(listOf(task(TaskStatus.PENDING).copy(subject = "Fix the tests")))
        )
    }
}
