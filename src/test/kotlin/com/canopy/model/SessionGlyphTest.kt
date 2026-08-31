package com.canopy.model

import com.canopy.toolwindow.isSpinnerFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionGlyphTest {

    @Test
    fun `a working session spins wherever it is drawn`() {
        val glyph = sessionGlyph(SessionAttention.Working, SessionPresence.OpenHere, 0)

        assertTrue(isSpinnerFrame(glyph.orEmpty()))
    }

    @Test
    fun `compacting spins too, because it is the agent busy with itself`() {
        val glyph = sessionGlyph(SessionAttention.Compacting, SessionPresence.OpenHere, 0)

        assertTrue(isSpinnerFrame(glyph.orEmpty()))
    }

    @Test
    fun `a dead terminal outranks whatever it last claimed to be doing`() {
        assertEquals("⊘", sessionGlyph(SessionAttention.Working, SessionPresence.Unresponsive, 0))
    }

    @Test
    fun `waiting for you beats where the session happens to be open`() {
        assertEquals(
            SessionAttention.WaitingForInput.glyph,
            sessionGlyph(SessionAttention.WaitingForInput, SessionPresence.OpenElsewhere, 0)
        )
    }

    @Test
    fun `a session open right here needs no mark saying so`() {
        assertNull(sessionGlyph(SessionAttention.None, SessionPresence.OpenHere, 0))
    }
}
