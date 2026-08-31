package com.canopy.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttentionGlyphTest {

    @Test
    fun `only a permission prompt is loud`() {
        assertEquals("!", SessionAttention.NeedsPermission.glyph)
    }

    @Test
    fun `a finished turn reads as a prompt, not a question`() {
        assertEquals("›", SessionAttention.WaitingForInput.glyph)
    }

    @Test
    fun `a permission prompt still sorts above a finished turn`() {
        assertTrue(SessionAttention.NeedsPermission.rank < SessionAttention.WaitingForInput.rank)
        assertTrue(SessionAttention.WaitingForInput.rank < SessionAttention.Working.rank)
    }

    @Test
    fun `both are what the list treats as blocking`() {
        assertTrue(SessionAttention.NeedsPermission.isBlocking)
        assertTrue(SessionAttention.WaitingForInput.isBlocking)
        assertTrue(!SessionAttention.Working.isBlocking)
    }
}
