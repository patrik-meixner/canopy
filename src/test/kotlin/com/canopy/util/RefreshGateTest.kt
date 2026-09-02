package com.canopy.util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RefreshGateTest {

    private val gate = RefreshGate(floorMillis = 5_000)

    @Test
    fun `the first request runs`() {
        assertTrue(gate.tryStart(isForced = false, now = 1_000))
    }

    @Test
    fun `a second request inside the floor is refused once the first has finished`() {
        gate.tryStart(isForced = false, now = 1_000)
        gate.finish()

        assertFalse(gate.tryStart(isForced = false, now = 3_000))
    }

    @Test
    fun `a request after the floor runs`() {
        gate.tryStart(isForced = false, now = 1_000)
        gate.finish()

        assertTrue(gate.tryStart(isForced = false, now = 6_000))
    }

    @Test
    fun `a forced request ignores the floor`() {
        gate.tryStart(isForced = false, now = 1_000)
        gate.finish()

        assertTrue(gate.tryStart(isForced = true, now = 1_001))
    }

    @Test
    fun `nothing runs twice at once, forced or not`() {
        gate.tryStart(isForced = false, now = 1_000)

        assertFalse(gate.tryStart(isForced = true, now = 9_000))
    }

    @Test
    fun `a refused request does not push the floor out`() {
        gate.tryStart(isForced = false, now = 1_000)
        gate.finish()
        gate.tryStart(isForced = false, now = 5_000)

        assertTrue(gate.tryStart(isForced = false, now = 6_000))
    }
}
