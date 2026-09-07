package com.canopy.terminal

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class PtyTraceTest {

    private val file = Files.createTempFile("canopy-pty-trace-", ".log")

    private fun linesAfter(write: PtyTrace.() -> Unit): List<String> {
        val trace = PtyTrace(file)
        trace.write()
        trace.close()

        return Files.readAllLines(file)
    }

    @Test
    fun `each direction keeps its own marker`() {
        assertEquals(
            listOf("> up", "< down"),
            linesAfter {
                input("up")
                output("down")
            }
        )
    }

    @Test
    fun `the control characters that would break a line are spelled out`() {
        assertEquals(
            listOf("< <ESC>[2J<CR><LF>"),
            linesAfter { output("\u001b[2J\r\n") }
        )
    }

    @Test
    fun `a burst is readable without waiting for the trace to be closed`() {
        val trace = PtyTrace(file)
        repeat(500) { trace.output("frame $it") }

        assertEquals(500, Files.readAllLines(file).size)
        trace.close()
    }
}
