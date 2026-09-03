package com.canopy.services

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionOpeningLineTest {

    @Test
    fun `a session is called after what was asked of it`() {
        assertEquals("fix the build", sessionOpeningLine("fix the build", "Done."))
    }

    @Test
    fun `a session resumed under a new id is called after the first thing the agent said`() {
        assertEquals("Done.", sessionOpeningLine(null, "Done."))
    }

    @Test
    fun `a transcript nobody has said anything in yet is not a session to list`() {
        assertNull(sessionOpeningLine(null, null))
        assertNull(sessionOpeningLine("", ""))
    }
}
