package com.canopy.toolwindow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FanOutTest {

    @Test
    fun `each worktree is named after the attempt it belongs to`() {
        assertEquals(listOf("retry-auth-1", "retry-auth-2", "retry-auth-3"), fanOutNames("retry auth", 3, emptySet()))
    }

    @Test
    fun `an existing worktree name is stepped over rather than collided with`() {
        val names = fanOutNames("retry", 2, setOf("retry-1"))

        assertEquals(listOf("retry-2", "retry-3"), names)
    }

    @Test
    fun `a prompt full of punctuation still names a branch`() {
        assertTrue(fanOutNames("fix it!!! ???", 1, emptySet()).single().startsWith("fix-it"))
    }

    @Test
    fun `asking for none creates none`() {
        assertEquals(emptyList<String>(), fanOutNames("x", 0, emptySet()))
    }
}
