package com.canopy.toolwindow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorktreeDiskUsageTest {

    @Test
    fun `the block count is read off the du line`() {
        assertEquals(4821L, parseDuKilobytes("4821\t/repo/.worktrees/feature"))
    }

    @Test
    fun `a permission error line is not mistaken for the total`() {
        val output = "du: /repo/.worktrees/x/private: Permission denied\n90112\t/repo/.worktrees/x"

        assertEquals(90112L, parseDuKilobytes(output))
    }

    @Test
    fun `output with no number reports nothing rather than zero`() {
        assertNull(parseDuKilobytes("du: illegal option -- k"))
    }

    @Test
    fun `sizes read in the unit a human would use`() {
        assertEquals("1.5 GB", formatSize(1_610_612_736))
        assertEquals("512.0 KB", formatSize(524_288))
        assertEquals("64 B", formatSize(64))
    }
}
