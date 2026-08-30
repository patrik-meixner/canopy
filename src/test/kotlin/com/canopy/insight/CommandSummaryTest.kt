package com.canopy.insight

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandSummaryTest {

    @Test
    fun `the directory change every command starts with is not the command`() {
        val summary = summarizeCommand("cd /Users/me/projects/canopy && git status --short")

        assertEquals("git", summary.verb)
        assertEquals("status --short", summary.rest)
    }

    @Test
    fun `environment set for one call is not the command either`() {
        val summary = summarizeCommand("JAVA_HOME=/opt/jdk ./gradlew test")

        assertEquals("gradlew", summary.verb)
        assertEquals("test", summary.rest)
    }

    @Test
    fun `a path to a binary reads as the binary`() {
        assertEquals("grep", summarizeCommand("/usr/bin/grep -n foo file").verb)
    }

    @Test
    fun `redirection noise is dropped`() {
        assertEquals("ls", summarizeCommand("ls -la 2>&1").rest.let { "ls" })
        assertEquals("-la", summarizeCommand("ls -la 2>&1").rest)
    }

    @Test
    fun `a bare directory change still says what it was`() {
        assertEquals("cd", summarizeCommand("cd /Users/me/projects/canopy && ").verb)
    }
}
