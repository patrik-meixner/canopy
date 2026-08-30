package com.canopy.toolwindow

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionMessageTest {

    private fun record(json: String): JsonObject = JsonParser.parseString(json).asJsonObject

    @Test
    fun `a typed message is listed`() {
        val message = humanMessageIn(record("""{"type":"user","message":{"content":"fix the build"}}"""), 1)

        assertEquals("fix the build", message?.text)
    }

    @Test
    fun `a tool result is not a message`() {
        val json = """{"type":"user","toolUseResult":{},"message":{"content":[{"type":"tool_result","content":"ok"}]}}"""

        assertNull(humanMessageIn(record(json), 1))
    }

    @Test
    fun `a tool result block alone is not a message`() {
        val json = """{"type":"user","message":{"content":[{"type":"tool_result","content":"ok"}]}}"""

        assertNull(humanMessageIn(record(json), 1))
    }

    @Test
    fun `the image marker the CLI writes back is not a message`() {
        val json = """{"type":"user","isMeta":true,"message":{"content":"[Image: source: /Users/me/x.png]"}}"""

        assertNull(humanMessageIn(record(json), 1))
    }

    @Test
    fun `an interrupt marker is not a message`() {
        val json = """{"type":"user","message":{"content":"[Request interrupted by user]"}}"""

        assertNull(humanMessageIn(record(json), 1))
    }

    @Test
    fun `a pasted image is carried with the text`() {
        val json = """{"type":"user","message":{"content":[
            {"type":"text","text":"look at this"},
            {"type":"image","source":{"type":"base64","media_type":"image/png","data":"AAAB"}}]}}"""
        val message = humanMessageIn(record(json), 3)

        assertEquals(listOf("AAAB"), message?.images)
        assertEquals("look at this", message?.text)
    }

    @Test
    fun `a message that is only an image still counts`() {
        val json = """{"type":"user","message":{"content":[{"type":"image","source":{"data":"AAAB"}}]}}"""

        assertEquals(1, humanMessageIn(record(json), 1)?.images?.size)
    }

    @Test
    fun `the headline skips leading blank lines`() {
        assertEquals("second", SessionMessage(1, "\n\n  second\nthird", emptyList()).headline)
    }
}
