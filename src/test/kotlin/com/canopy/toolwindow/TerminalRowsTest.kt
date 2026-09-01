package com.canopy.toolwindow

import com.canopy.model.SessionDisplay
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class TerminalRowsTest {

    private fun session(id: String) = SessionDisplay(
        sessionId = id,
        name = id,
        firstPrompt = "",
        messageCount = 0,
        modified = Instant.EPOCH,
        gitBranch = null,
        projectPath = "/p"
    )

    private fun rowsFor(vararg terminals: TerminalSource) =
        withTerminalRows(listOf(session("a"), session("b")), terminals.toList(), "/p", 0L)
            .map { it.sessionId }

    @Test
    fun `a terminal sits directly under the session it belongs to`() {
        assertEquals(listOf("a", "t1", "b"), rowsFor(TerminalSource("t1", "Terminal", "a")))
    }

    @Test
    fun `a terminal whose session is not listed is left out`() {
        assertEquals(listOf("a", "b"), rowsFor(TerminalSource("t1", "Terminal", "gone")))
    }

    @Test
    fun `several terminals of one session keep the order they were opened in`() {
        val rows = rowsFor(
            TerminalSource("t1", "Terminal", "b"),
            TerminalSource("t2", "Terminal", "b")
        )

        assertEquals(listOf("a", "b", "t1", "t2"), rows)
    }

    @Test
    fun `a terminal row knows which session it belongs to`() {
        val rows = withTerminalRows(listOf(session("a")), listOf(TerminalSource("t1", "be", "a")), "/p", 0L)

        assertEquals("a", rows[1].terminalOwner)
        assertEquals(true, rows[1].isTerminal)
        assertEquals(false, rows[0].isTerminal)
    }
}
