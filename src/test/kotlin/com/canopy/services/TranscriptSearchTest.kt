package com.canopy.services

import com.canopy.toolwindow.agentTextIn
import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranscriptSearchTest {

    private fun record(json: String): JsonObject = Gson().fromJson(json, JsonObject::class.java)

    @Test
    fun `what the agent said is searchable`() {
        val said = agentTextIn(
            record("""{"type":"assistant","message":{"content":[{"type":"text","text":"Correction to what I just said"}]}}""")
        )

        assertEquals("Correction to what I just said", said)
    }

    @Test
    fun `a turn that only ran tools has nothing to match`() {
        val said = agentTextIn(
            record("""{"type":"assistant","message":{"content":[{"type":"tool_use","name":"Bash","input":{"command":"git status"}}]}}""")
        )

        assertNull(said)
    }

    @Test
    fun `thinking is not something the agent said`() {
        val said = agentTextIn(
            record("""{"type":"assistant","message":{"content":[{"type":"thinking","thinking":"Correction: maybe not"}]}}""")
        )

        assertNull(said)
    }

    @Test
    fun `a user record is left to the human reader`() {
        val said = agentTextIn(record("""{"type":"user","message":{"content":[{"type":"text","text":"Correction to what I just said"}]}}"""))

        assertNull(said)
    }
}
