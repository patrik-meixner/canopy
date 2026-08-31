package com.canopy.services

import org.junit.Assert.assertEquals
import org.junit.Test

class ShellCommandLineTest {

    @Test
    fun `a plain command is passed through unquoted`() {
        assertEquals("claude --resume abc-123", shellCommandLine(listOf("claude", "--resume", "abc-123")))
    }

    @Test
    fun `a path with spaces survives the shell`() {
        assertEquals(
            "claude --settings '/tmp/my settings.json'",
            shellCommandLine(listOf("claude", "--settings", "/tmp/my settings.json"))
        )
    }

    @Test
    fun `an argument holding a quote cannot end the quoting`() {
        assertEquals("""'it'\''s'""", shellCommandLine(listOf("it's")))
    }

    @Test
    fun `an empty argument stays an argument`() {
        assertEquals("claude ''", shellCommandLine(listOf("claude", "")))
    }
}
