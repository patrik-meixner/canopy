package com.canopy.insight

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityDetailTest {

    private fun input(json: String): JsonObject = JsonParser.parseString(json).asJsonObject

    @Test
    fun `an edit is described by the file it changes`() {
        assertEquals("/repo/App.kt", activityDetail("Edit", input("""{"file_path":"/repo/App.kt","old_string":"a"}""")))
    }

    @Test
    fun `a shell call is described by its first command line`() {
        assertEquals("git status", activityDetail("Bash", input("""{"command":"git status\nsecond line"}""")))
    }

    @Test
    fun `a long command is cut rather than filling the row`() {
        val detail = activityDetail("Bash", input("""{"command":"${"x".repeat(400)}"}"""))

        assertEquals(120, detail.length)
        assertEquals('…', detail.last())
    }

    @Test
    fun `a call with nothing worth naming shows nothing`() {
        assertEquals("", activityDetail("Bash", input("""{"timeout":5}""")))
    }
}
