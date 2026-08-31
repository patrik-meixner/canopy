package com.canopy.insight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EasingTest {

    @Test
    fun `the glide starts where it is and ends where it was sent`() {
        assertEquals(0.0, eased(0.0), 0.0001)
        assertEquals(1.0, eased(1.0), 0.0001)
    }

    @Test
    fun `it covers most of the distance early, so it settles rather than stops dead`() {
        assertTrue(eased(0.5) > 0.7)
        assertTrue(eased(0.9) > eased(0.8))
    }
}
