package com.canopy.services

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShouldSchedulePollTest {

    @Test
    fun `the first monitored session starts the loop`() {
        assertTrue(shouldSchedulePoll(isLoopRunning = false, monitoredCount = 1))
    }

    @Test
    fun `a session added while the loop is stopped starts it, however many are already monitored`() {
        assertTrue(shouldSchedulePoll(isLoopRunning = false, monitoredCount = 4))
    }

    @Test
    fun `a session added to a running loop does not start a second one`() {
        assertFalse(shouldSchedulePoll(isLoopRunning = true, monitoredCount = 2))
    }

    @Test
    fun `nothing left to watch leaves the loop stopped`() {
        assertFalse(shouldSchedulePoll(isLoopRunning = false, monitoredCount = 0))
    }
}
