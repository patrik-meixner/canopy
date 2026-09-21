package com.canopy.toolwindow

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitDiffEntriesTest {

    private val output = listOf(
        ":100644 100644 de98044 0000000 M\tcontent.txt",
        ":100644 100755 587be6b 0000000 M\tmodeonly.txt",
        ":100644 100644 742c16a 0000000 M\tpic.bin",
        ":160000 160000 754dd5e 754dd5e M\tsub",
        ":100644 100644 3367afd 3367afd R100\twas.txt\tnow.txt",
        "1\t1\tcontent.txt",
        "0\t0\tmodeonly.txt",
        "-\t-\tpic.bin",
        "0\t0\tsub",
        "0\t0\twas.txt => now.txt"
    ).joinToString("\n")

    private fun entry(path: String) = parseGitDiffEntries(output).single { it.newPath == path }

    @Test
    fun `a file whose lines changed reports a content change`() {
        assertTrue(entry("content.txt").hasContentChange)
        assertFalse(entry("content.txt").modeChanged)
    }

    @Test
    fun `a file whose only change is its permissions has no content change`() {
        val modeOnly = entry("modeonly.txt")

        assertFalse(modeOnly.hasContentChange)
        assertTrue(modeOnly.modeChanged)
        assertFalse(modeOnly.isSubmodule)
    }

    @Test
    fun `a submodule is not a file whose permissions changed`() {
        val submodule = entry("sub")

        assertTrue(submodule.isSubmodule)
        assertFalse(submodule.modeChanged)
    }

    @Test
    fun `a binary git cannot count lines for is not called unchanged`() {
        assertTrue(entry("pic.bin").hasContentChange)
    }

    @Test
    fun `a rename keeps both of its names`() {
        val renamed = entry("now.txt")

        assertEquals('R', renamed.status)
        assertEquals("was.txt", renamed.oldPath)
        assertFalse(renamed.hasContentChange)
    }

    @Test
    fun `nothing changed means no entries`() {
        assertEquals(emptyList(), parseGitDiffEntries(""))
    }
}
