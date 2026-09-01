package com.canopy.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AttentionAfterCompactTest {

    @Test
    fun `a session sitting on the summary a compact left behind is waiting, not working`() {
        val attention = sessionAttentionFor(
            notifyState = null,
            isRunning = true,
            tail = TranscriptTail.Housekeeping
        )

        assertEquals(SessionAttention.WaitingForInput, attention)
    }

    @Test
    fun `a session in the middle of a tool call is still working`() {
        val attention = sessionAttentionFor(
            notifyState = null,
            isRunning = true,
            tail = TranscriptTail.AgentsTurn
        )

        assertEquals(SessionAttention.Working, attention)
    }
}
