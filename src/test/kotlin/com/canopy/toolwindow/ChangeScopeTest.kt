package com.canopy.toolwindow

import com.canopy.model.SessionDisplay
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChangeScopeTest {

    private val workspace = setOf("/ws", "/ws/dmm")

    private fun session(touched: Map<String, Int>, startedAt: Instant? = Instant.ofEpochSecond(1000)) = SessionDisplay(
        sessionId = "s1",
        name = "s",
        firstPrompt = "p",
        messageCount = 1,
        modified = Instant.now(),
        gitBranch = null,
        projectPath = "/ws",
        touchedRoots = touched,
        startedAt = startedAt
    )

    @Test
    fun `a session that has not been parsed yet still reviews the workspace`() {
        val scope = changeScopeFor(null, workspace)

        assertEquals(workspace, scope.roots)
        assertNull(scope.since)
    }

    @Test
    fun `a session that wrote somewhere reviews exactly there`() {
        val scope = changeScopeFor(session(mapOf("/ws/dmm" to 4)), workspace)

        assertEquals(setOf("/ws/dmm"), scope.roots)
    }

    @Test
    fun `a session that recorded nothing falls back to the workspace`() {
        assertEquals(workspace, changeScopeFor(session(emptyMap()), workspace).roots)
    }

    @Test
    fun `a resolved session bounds the commit sections by its own start`() {
        val startedAt = Instant.ofEpochSecond(4242)

        assertEquals(startedAt, changeScopeFor(session(emptyMap(), startedAt), workspace).since)
    }
}
