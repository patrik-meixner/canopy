package com.canopy.editor

import kotlin.test.Test
import kotlin.test.assertEquals

class MonitoringIdOfTest {

    @Test
    fun `a tab with no session yet is monitored under its own key`() {
        assertEquals("new-1", monitoringIdOf(sessionId = null, sessionKey = "new-1"))
    }

    @Test
    fun `a linked tab is monitored under the session it shows`() {
        assertEquals("abc-123", monitoringIdOf(sessionId = "abc-123", sessionKey = "new-1"))
    }

    @Test
    fun `the same tab asked twice answers the same, so its files stay readable`() {
        assertEquals(
            monitoringIdOf(sessionId = null, sessionKey = "new-1"),
            monitoringIdOf(sessionId = null, sessionKey = "new-1")
        )
    }
}
