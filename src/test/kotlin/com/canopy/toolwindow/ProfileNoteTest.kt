package com.canopy.toolwindow

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProfileNoteTest {

    @Test
    fun `a session on a named account says which one`() {
        assertEquals("Personal", profileNote("Personal", "Default"))
    }

    @Test
    fun `the account everything else uses is not worth a line`() {
        assertNull(profileNote("Default", "Default"))
    }

    @Test
    fun `a session that never chose says nothing`() {
        assertNull(profileNote(null, "Default"))
    }

    @Test
    fun `with no default to compare against a name still shows`() {
        assertEquals("Personal", profileNote("Personal", null))
    }
}
