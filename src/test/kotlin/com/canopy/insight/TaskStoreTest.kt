package com.canopy.insight

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskStoreTest {

    private fun task(json: String): JsonObject = JsonParser.parseString(json).asJsonObject

    @Test
    fun `a stored task keeps everything the panel shows`() {
        val json = """{"id":"115","subject":"Ship the image","description":"why","activeForm":"Shipping",
            "status":"completed","blocks":[],"blockedBy":["114"]}"""
        val parsed = plannedTaskOf(task(json))

        assertEquals("115", parsed?.id)
        assertEquals("Ship the image", parsed?.subject)
        assertEquals(TaskStatus.COMPLETED, parsed?.status)
        assertEquals(listOf("114"), parsed?.blockedBy)
    }

    @Test
    fun `an unknown status reads as pending rather than dropping the task`() {
        assertEquals(TaskStatus.PENDING, statusOf("something-new"))
        assertEquals(TaskStatus.PENDING, statusOf(null))
    }

    @Test
    fun `in progress is what the CLI marks as running`() {
        assertEquals(TaskStatus.IN_PROGRESS, statusOf("in_progress"))
    }

    @Test
    fun `a file without an id is not a task`() {
        assertNull(plannedTaskOf(task("""{"subject":"orphan"}""")))
    }

    @Test
    fun `the directory is keyed on the session`() {
        val directory = TaskStore.directoryFor("abc-123")

        assertEquals("abc-123", directory.fileName.toString())
        assertEquals("tasks", directory.parent.fileName.toString())
    }
}
