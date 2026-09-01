package com.canopy.toolwindow

import com.canopy.model.SessionDisplay
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaceLineTest {

    private val now = Instant.parse("2026-09-01T10:00:00Z")

    private fun session(touched: Map<String, Int> = emptyMap()) = SessionDisplay(
        sessionId = "s1",
        name = "canopy",
        firstPrompt = "p",
        messageCount = 4889,
        modified = now.minusSeconds(3 * 3600),
        gitBranch = null,
        projectPath = "/Users/me/projects/urbido-2.0",
        touchedRoots = touched
    )

    @Test
    fun `with no session the line says what opening one would do`() {
        assertEquals("Open a session to review what it changed", placeLine(null, now.toEpochMilli()))
    }

    @Test
    fun `where it wrote leads, then how long ago, then how much was said`() {
        val line = placeLine(session(mapOf("/Users/me/projects/canopy" to 9)), now.toEpochMilli())

        assertEquals("canopy   3h ago   4889 messages", line)
    }

    @Test
    fun `a session that wrote nowhere still says where it ran`() {
        assertTrue(placeLine(session(), now.toEpochMilli()).startsWith("urbido-2.0"))
    }
}
