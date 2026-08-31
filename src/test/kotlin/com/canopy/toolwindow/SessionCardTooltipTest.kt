package com.canopy.toolwindow

import com.canopy.model.SessionDisplay
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCardTooltipTest {

    private fun session(name: String?, worktree: String? = null) = SessionDisplay(
        sessionId = "s1",
        name = name,
        firstPrompt = "prompt",
        messageCount = 1,
        modified = Instant.now(),
        gitBranch = null,
        projectPath = "/Users/me/projects/canopy",
        worktreeName = worktree
    )

    @Test
    fun `the full name lives in the tooltip, since the card cuts it`() {
        val tooltip = tooltipFor(session("Na základě téhle konverzace vymysli krátký název"))

        assertTrue(tooltip.contains("Na základě téhle konverzace vymysli krátký název"))
    }

    @Test
    fun `a worktree session names its worktree rather than the project path`() {
        assertTrue(tooltipFor(session("fix", worktree = "auth-refactor")).contains("auth-refactor"))
    }

    @Test
    fun `markup in a session name cannot break the tooltip`() {
        assertTrue(tooltipFor(session("<b>bold</b>")).contains("&lt;b&gt;bold&lt;/b&gt;"))
    }

    @Test
    fun `nothing to say means no tooltip window at all`() {
        assertEquals("", tooltipFor(session(null).copy(firstPrompt = "", projectPath = "")))
    }
}
