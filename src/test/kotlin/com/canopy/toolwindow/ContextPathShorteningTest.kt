package com.canopy.toolwindow

import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Test

class ContextPathShorteningTest {

    @Test
    fun `a path under home reads as a tilde path`() {
        val home = System.getProperty("user.home")

        assertEquals("~/.claude/rules", shorten(Path.of("$home/.claude/rules")))
    }

    @Test
    fun `a path outside home keeps its full form`() {
        assertEquals("/opt/shared/.claude", shorten(Path.of("/opt/shared/.claude")))
    }
}
