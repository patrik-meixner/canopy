package com.canopy.services

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionProfilesTest {

    private val profiles = SessionProfiles().also { it.loadState(SessionProfiles.State()) }

    @Test
    fun `a session resumed under a new id keeps the profile it was running on`() {
        profiles.remember("new-1", "Personal")
        profiles.rekey("new-1", "abc-123")

        assertEquals("Personal", profiles.of("abc-123"))
        assertNull(profiles.of("new-1"))
    }

    @Test
    fun `rekeying a session that never chose a profile invents none`() {
        profiles.rekey("new-1", "abc-123")

        assertNull(profiles.of("abc-123"))
    }

    @Test
    fun `an id that resumes to itself keeps its profile`() {
        profiles.remember("abc-123", "Personal")
        profiles.rekey("abc-123", "abc-123")

        assertEquals("Personal", profiles.of("abc-123"))
    }

    @Test
    fun `clearing a session's profile puts it back on the default`() {
        profiles.remember("abc-123", "Personal")
        profiles.remember("abc-123", null)

        assertNull(profiles.of("abc-123"))
    }
}
