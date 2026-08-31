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

class CommitsWithFilesTest {

    private val unit = '\u001F'
    private val marker = '\u0001'

    private fun log(vararg lines: String) = lines.joinToString("\n")

    @Test
    fun `each commit collects the files listed under it`() {
        val output = log(
            "${marker}sha1${unit}first${unit}Me${unit}1700000000",
            "",
            "M\tApp.kt",
            "A\tNew.kt",
            "${marker}sha2${unit}second${unit}Me${unit}1699999999",
            "",
            "D\tOld.kt"
        )
        val parsed = parseCommitsWithFiles(output, "/ws", "canopy")

        assertEquals(listOf("sha1", "sha2"), parsed.map { it.first.sha })
        assertEquals(2, parsed[0].second.size)
        assertEquals(listOf("Old.kt"), parsed[1].second.map { it.second })
    }

    @Test
    fun `a subject that looks like a status line is still a subject`() {
        val output = log("${marker}sha1${unit}M\tApp.kt${unit}Me${unit}1700000000", "", "M\tReal.kt")
        val parsed = parseCommitsWithFiles(output, "/ws", "r")

        assertEquals(1, parsed.size)
        assertEquals(listOf("Real.kt"), parsed.single().second.map { it.second })
    }

    @Test
    fun `a commit that changed nothing carries no files`() {
        val parsed = parseCommitsWithFiles("${marker}sha1${unit}empty${unit}Me${unit}1700000000", "/ws", "r")

        assertEquals(emptyList<Triple<Char, String, String>>(), parsed.single().second)
    }

    @Test
    fun `files before any commit header are ignored rather than crashing`() {
        assertEquals(0, parseCommitsWithFiles("M\tOrphan.kt", "/ws", "r").size)
    }
}
