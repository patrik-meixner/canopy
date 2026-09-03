package com.canopy.toolwindow

import com.canopy.model.SessionDisplay
import com.canopy.settings.SessionRestore
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RestorePlanTest {

    private val saved = listOf(session("a"), session("b"), session("c"))

    @Test
    fun `restoring everything stages the first and runs the rest without a tab`() {
        val plan = restorePlan(saved, SessionRestore.All)

        assertEquals("a", plan.staged?.sessionId)
        assertEquals(listOf("b", "c"), plan.detached.map { it.sessionId })
    }

    @Test
    fun `restoring the last session leaves the others stopped`() {
        val plan = restorePlan(saved, SessionRestore.LastOnly)

        assertEquals("a", plan.staged?.sessionId)
        assertEquals(emptyList(), plan.detached)
    }

    @Test
    fun `restoring nothing starts no agent at all`() {
        val plan = restorePlan(saved, SessionRestore.None)

        assertNull(plan.staged)
        assertEquals(emptyList(), plan.detached)
    }

    @Test
    fun `nothing saved is nothing to restore, whatever the setting says`() {
        val plan = restorePlan(emptyList(), SessionRestore.All)

        assertNull(plan.staged)
        assertEquals(emptyList(), plan.detached)
    }

    private fun session(id: String) = SessionDisplay(
        sessionId = id,
        name = id,
        firstPrompt = id,
        messageCount = 1,
        modified = Instant.EPOCH,
        gitBranch = null,
        projectPath = "/ws"
    )
}
