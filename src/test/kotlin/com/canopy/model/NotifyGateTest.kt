package com.canopy.model

import org.junit.Assert.assertEquals
import org.junit.Test

class NotifyGateTest {

    @Test
    fun `running a tool is not waiting for anything`() {
        assertEquals(SessionAttention.Working, sessionAttentionOf("tool:Bash"))
        assertEquals(SessionAttention.Working, sessionAttentionOf("tool:Edit"))
    }

    @Test
    fun `only a permission prompt actually blocks the agent`() {
        assertEquals(SessionAttention.NeedsPermission, sessionAttentionOf("permission_prompt"))
    }

    @Test
    fun `finishing a turn is its own state, not a permission prompt`() {
        assertEquals(SessionAttention.WaitingForInput, sessionAttentionOf("idle_prompt"))
    }

    @Test
    fun `compacting is work, however long it takes`() {
        assertEquals(SessionAttention.Compacting, sessionAttentionOf("compact"))
    }

    @Test
    fun `an unknown state asks for nothing rather than guessing`() {
        assertEquals(SessionAttention.None, sessionAttentionOf("something-new"))
        assertEquals(SessionAttention.None, sessionAttentionOf(null))
    }
}
