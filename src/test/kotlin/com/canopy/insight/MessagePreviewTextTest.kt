package com.canopy.insight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessagePreviewTextTest {

    @Test
    fun `a long message is cut to the card and marked as cut`() {
        val text = (1..20).joinToString("\n") { "line $it" }
        val preview = previewOf(text)

        assertEquals(5, preview.lines().size)
        assertTrue(preview.endsWith("…"))
    }

    @Test
    fun `a short message is shown whole with no marker`() {
        assertEquals("one\ntwo", previewOf("one\n\n  two  "))
    }
}
