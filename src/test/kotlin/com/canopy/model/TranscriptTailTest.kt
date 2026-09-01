package com.canopy.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptTailTest {

    @Test
    fun `an assistant message hands the turn back`() {
        assertEquals(TranscriptTail.AssistantReply, tailOf("assistant", "Done."))
    }

    @Test
    fun `a prompt nobody has answered yet is the agent's turn`() {
        assertEquals(TranscriptTail.AgentsTurn, tailOf("user", "fix the build"))
    }

    @Test
    fun `a tool result is the middle of a turn`() {
        assertEquals(
            TranscriptTail.AgentsTurn,
            transcriptTailOf("user", isCompactSummary = false, isMeta = false, hasToolUseResult = true, text = "")
        )
    }

    @Test
    fun `the summary a compact leaves behind is nobody's turn`() {
        assertEquals(
            TranscriptTail.Housekeeping,
            transcriptTailOf(
                "user",
                isCompactSummary = true,
                isMeta = false,
                hasToolUseResult = false,
                text = "This session is being continued from a previous conversation"
            )
        )
    }

    @Test
    fun `the echo of a slash command is nobody's turn`() {
        assertEquals(TranscriptTail.Housekeeping, tailOf("user", "<command-name>/compact</command-name>"))
    }

    @Test
    fun `what a slash command printed is nobody's turn`() {
        assertEquals(TranscriptTail.Housekeeping, tailOf("user", "<local-command-stdout>Compacted</local-command-stdout>"))
    }

    @Test
    fun `a caveat the CLI injects is nobody's turn`() {
        assertEquals(
            TranscriptTail.Housekeeping,
            transcriptTailOf("user", isCompactSummary = false, isMeta = true, hasToolUseResult = false, text = "Caveat")
        )
    }

    @Test
    fun `a record that is neither side of the conversation leaves the tail alone`() {
        assertEquals(null, tailOf("frame-link", ""))
    }
}

private fun tailOf(type: String, text: String) =
    transcriptTailOf(type, isCompactSummary = false, isMeta = false, hasToolUseResult = false, text = text)
