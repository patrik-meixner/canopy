package com.canopy.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionAttentionTest {

    @Test
    fun `a tool running right now is working`() {
        val attention = sessionAttentionFor("tool:Bash", isRunning = true, tail = TranscriptTail.AgentsTurn, idleForMillis = 1_000)

        assertEquals(SessionAttention.Working, attention)
    }

    @Test
    fun `a tool state left behind by an interrupted turn stops claiming to be working`() {
        val attention = sessionAttentionFor(
            "tool:Bash",
            isRunning = true,
            tail = TranscriptTail.AssistantReply,
            idleForMillis = LONG_AFTER_ANY_TOOL
        )

        assertEquals(SessionAttention.WaitingForInput, attention)
    }

    @Test
    fun `a permission prompt never goes stale, because nothing else will clear it`() {
        val attention = sessionAttentionFor(
            "permission_prompt",
            isRunning = true,
            tail = TranscriptTail.AssistantReply,
            idleForMillis = LONG_AFTER_ANY_TOOL
        )

        assertEquals(SessionAttention.NeedsPermission, attention)
    }

    @Test
    fun `a stale tool state on a session that is not running at all is simply idle`() {
        val attention = sessionAttentionFor(
            "tool:Bash",
            isRunning = false,
            tail = TranscriptTail.AssistantReply,
            idleForMillis = LONG_AFTER_ANY_TOOL
        )

        assertEquals(SessionAttention.None, attention)
    }
}

private const val LONG_AFTER_ANY_TOOL = 24L * 60 * 60 * 1000
