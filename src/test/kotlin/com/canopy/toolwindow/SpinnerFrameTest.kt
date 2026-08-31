package com.canopy.toolwindow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SpinnerFrameTest {

    @Test
    fun `the frame advances with time`() {
        assertNotEquals(spinnerFrame(0), spinnerFrame(SPINNER_FRAME_MS))
    }

    @Test
    fun `rows repainted at different moments of the same frame agree`() {
        assertEquals(spinnerFrame(SPINNER_FRAME_MS), spinnerFrame(SPINNER_FRAME_MS + SPINNER_FRAME_MS - 1))
    }

    @Test
    fun `the sequence wraps rather than running out`() {
        assertEquals(spinnerFrame(0), spinnerFrame(SPINNER_FRAME_MS * 10))
    }
}
