package com.canopy.toolwindow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckpointsTest {

    @Test
    fun `a ref name survives a label that is free text`() {
        val ref = Checkpoints.refFor("abc-123", 1700000000000, "after turn 4: fix the build!")

        assertTrue(ref.startsWith("refs/canopy/abc-123/1700000000000-"))
        assertTrue(ref.none { it.isWhitespace() })
    }

    @Test
    fun `a label of nothing but punctuation still makes a usable ref`() {
        assertEquals("checkpoint", slug("!!! ???"))
    }

    @Test
    fun `listing reads the time back out of the ref`() {
        val parsed = parseCheckpoints("refs/canopy/s1/1700000000000-after-turn-4|abc123\n")

        assertEquals(1700000000000, parsed.single().atMillis)
        assertEquals("abc123", parsed.single().commit)
        assertEquals("after turn 4", parsed.single().label)
    }

    @Test
    fun `a ref that is not a checkpoint is skipped rather than shown as epoch zero`() {
        assertEquals(emptyList<Checkpoint>(), parseCheckpoints("refs/heads/main|abc123\n"))
    }

    @Test
    fun `a line without a commit is not a checkpoint`() {
        assertEquals(emptyList<Checkpoint>(), parseCheckpoints("refs/canopy/s1/1700000000000-x|\n"))
    }
}
