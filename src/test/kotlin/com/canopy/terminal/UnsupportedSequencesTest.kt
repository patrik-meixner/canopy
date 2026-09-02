package com.canopy.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class UnsupportedSequencesTest {

    private val escape = ""

    private fun stripped(text: String, isSynchronizedOutputSupported: Boolean = false) =
        unsupportedSequences(isSynchronizedOutputSupported).replace(text, "")

    @Test
    fun `restoring the cursor survives, because the terminal supports it`() {
        assertEquals("a${escape}[ub", stripped("a${escape}[ub"))
    }

    @Test
    fun `popping the kitty keyboard mode is dropped`() {
        assertEquals("ab", stripped("a${escape}[<ub"))
    }

    @Test
    fun `synchronized output is dropped at both ends when the emulator cannot batch on it`() {
        assertEquals("ab", stripped("${escape}[?2026ha${escape}[?2026lb"))
    }

    @Test
    fun `synchronized output survives when the emulator batches a frame on it`() {
        val frame = "${escape}[?2026ha${escape}[?2026lb"

        assertEquals(frame, stripped(frame, isSynchronizedOutputSupported = true))
    }

    @Test
    fun `the rest is still dropped when synchronized output survives`() {
        assertEquals("ab", stripped("a${escape}[<ub${escape}[>4;2m", isSynchronizedOutputSupported = true))
    }

    @Test
    fun `modifyOtherKeys is dropped`() {
        assertEquals("ab", stripped("a${escape}[>4;2mb"))
    }
}
