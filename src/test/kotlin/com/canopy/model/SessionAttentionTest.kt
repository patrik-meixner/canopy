package com.canopy.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionAttentionTest {

    @Test
    fun `a permission prompt outranks everything else`() {
        val ranks = listOf(
            sessionAttentionOf("permission_prompt"),
            sessionAttentionOf("idle_prompt"),
            sessionAttentionOf("tool:Bash"),
            sessionAttentionOf(null)
        ).map { it.rank }

        assertEquals(ranks.sorted(), ranks)
    }

    @Test
    fun `any tool counts as working`() {
        assertEquals(SessionAttention.Working, sessionAttentionOf("tool:Edit"))
        assertEquals(SessionAttention.Working, sessionAttentionOf("tool:WebSearch"))
    }

    @Test
    fun `only prompts block you`() {
        assertTrue(sessionAttentionOf("permission_prompt").isBlocking)
        assertTrue(sessionAttentionOf("idle_prompt").isBlocking)
        assertFalse(sessionAttentionOf("tool:Bash").isBlocking)
        assertFalse(sessionAttentionOf("compact").isBlocking)
    }

    @Test
    fun `a finished session that is not running never asks for attention`() {
        assertEquals(
            SessionAttention.None,
            sessionAttentionFor(notifyState = null, isRunning = false, lastEntryRole = "assistant")
        )
    }

    @Test
    fun `an external session whose last word was the agent's is waiting for you`() {
        assertEquals(
            SessionAttention.WaitingForInput,
            sessionAttentionFor(notifyState = null, isRunning = true, lastEntryRole = "assistant")
        )
    }

    @Test
    fun `an external session still answering counts as working`() {
        assertEquals(
            SessionAttention.Working,
            sessionAttentionFor(notifyState = null, isRunning = true, lastEntryRole = "user")
        )
    }

    @Test
    fun `the notify state wins over the transcript guess`() {
        assertEquals(
            SessionAttention.NeedsPermission,
            sessionAttentionFor(notifyState = "permission_prompt", isRunning = true, lastEntryRole = "user")
        )
    }

    @Test
    fun `an unknown state is not treated as attention`() {
        assertEquals(SessionAttention.None, sessionAttentionOf("something_new"))
    }
}
