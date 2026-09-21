package com.canopy.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProfileToRunTest {

    private val known = listOf(
        ClaudeProfile("Default", ".claude"),
        ClaudeProfile("Personal", ".claude-personal")
    )

    @Test
    fun `asking for a profile beats the one the session ran under before`() {
        assertEquals("Personal", profileToRun(chosen = "Personal", remembered = "Default", known = known)?.name)
    }

    @Test
    fun `a session reopened without a choice comes back on the profile it last ran under`() {
        assertEquals("Personal", profileToRun(chosen = null, remembered = "Personal", known = known)?.name)
    }

    @Test
    fun `a session that has never run has no profile of its own`() {
        assertNull(profileToRun(chosen = null, remembered = null, known = known))
    }

    @Test
    fun `a profile that has since been removed does not silently pick another`() {
        assertNull(profileToRun(chosen = null, remembered = "Gone", known = known))
        assertNull(profileToRun(chosen = "Gone", remembered = "Personal", known = known))
    }
}
