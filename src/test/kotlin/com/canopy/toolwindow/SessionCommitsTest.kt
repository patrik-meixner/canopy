package com.canopy.toolwindow

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionCommitsTest {

    private val unit = '\u001F'

    @Test
    fun `a commit carries what the row shows`() {
        val line = "abc123${unit}fix the build${unit}Patrik${unit}1700000000"
        val commit = parseCommits(line, "/ws", "canopy").single()

        assertEquals("abc123", commit.sha)
        assertEquals("fix the build", commit.subject)
        assertEquals("Patrik", commit.author)
        assertEquals(1700000000000, commit.atMillis)
        assertEquals("canopy", commit.repository)
    }

    @Test
    fun `a subject containing tabs and pipes survives the split`() {
        val line = "abc${unit}feat: a|b\tc${unit}Me${unit}1700000000"

        assertEquals("feat: a|b\tc", parseCommits(line, "/ws", "r").single().subject)
    }

    @Test
    fun `a truncated line is skipped rather than half-read`() {
        assertEquals(emptyList<CommitEntry>(), parseCommits("abc${unit}subject", "/ws", "r"))
    }

    @Test
    fun `a rename names both sides, so the diff has something to compare`() {
        val parsed = parseNameStatusLines("R100\told.kt\tnew.kt")

        assertEquals(Triple('R', "old.kt", "new.kt"), parsed.single())
    }

    @Test
    fun `an ordinary change names the same file twice`() {
        assertEquals(Triple('M', "App.kt", "App.kt"), parseNameStatusLines("M\tApp.kt").single())
    }
}
