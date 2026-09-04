package com.canopy.util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoalescedRunTest {

    private val run = CoalescedRun()

    @Test
    fun `the first request of a burst is the one that gets scheduled`() {
        assertTrue(run.claim())
    }

    @Test
    fun `the rest of the burst joins it rather than replacing it`() {
        run.claim()

        assertFalse(run.claim())
        assertFalse(run.claim())
    }

    @Test
    fun `a burst that never ends still runs, because nothing cancels what is pending`() {
        assertTrue(run.claim())
        repeat(1_000) { assertFalse(run.claim()) }
        run.done()

        assertTrue(run.claim())
    }
}
