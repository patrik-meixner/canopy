package com.canopy.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionIdToLinkTest {

    @Test
    fun `a tab with no session yet takes the one its terminal reports`() {
        assertEquals("s1", sessionIdToLink(reported = "s1", linked = null))
    }

    @Test
    fun `a session that came back under a new id takes the tab with it`() {
        assertEquals("s2", sessionIdToLink(reported = "s2", linked = "s1"))
    }

    @Test
    fun `the id it already has is not news`() {
        assertNull(sessionIdToLink(reported = "s1", linked = "s1"))
    }

    @Test
    fun `a status with no session in it links nothing`() {
        assertNull(sessionIdToLink(reported = null, linked = null))
        assertNull(sessionIdToLink(reported = " ", linked = null))
    }
}
