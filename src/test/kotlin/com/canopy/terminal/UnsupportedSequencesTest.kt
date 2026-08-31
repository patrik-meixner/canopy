package com.canopy.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class UnsupportedSequencesTest {

    private val escape = "\u001b"

    private fun stripped(text: String) = unsupportedSequences.replace(text, "")

    @Test
    fun `restoring the cursor survives, because the terminal supports it`() {
        assertEquals("a${escape}[ub", stripped("a${escape}[ub"))
    }

    @Test
    fun `popping the kitty keyboard mode is dropped`() {
        assertEquals("ab", stripped("a${escape}[<ub"))
    }

    @Test
    fun `synchronized output is dropped at both ends`() {
        assertEquals("ab", stripped("${escape}[?2026ha${escape}[?2026lb"))
    }

    @Test
    fun `modifyOtherKeys is dropped`() {
        assertEquals("ab", stripped("a${escape}[>4;2mb"))
    }
}
