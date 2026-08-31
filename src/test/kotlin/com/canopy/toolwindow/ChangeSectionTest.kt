package com.canopy.toolwindow

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangeSectionTest {

    @Test
    fun `what has left the machine is not the reviewer's work any more`() {
        assertFalse(SessionChangeSection.Pushed.isYours)
    }

    @Test
    fun `everything still on this machine is`() {
        assertTrue(SessionChangeSection.Uncommitted.isYours)
        assertTrue(SessionChangeSection.Committed.isYours)
        assertTrue(SessionChangeSection.Unversioned.isYours)
    }

    @Test
    fun `each section says what its state means, not just its name`() {
        SessionChangeSection.entries.forEach { assertTrue(it.hint.isNotBlank()) }
    }
}
