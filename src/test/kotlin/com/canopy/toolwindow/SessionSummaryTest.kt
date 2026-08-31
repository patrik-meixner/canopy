package com.canopy.toolwindow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSummaryTest {

    private fun summary(
        uncommitted: Int = 0,
        untracked: Int = 0,
        unpushed: Int = 0,
        pushed: Int = 0,
        repositories: Int = 1
    ) = outstandingSummary(uncommitted, untracked, unpushed, pushed, repositories)

    @Test
    fun `a clean tree says so instead of listing three zeroes`() {
        assertEquals("Nothing outstanding", summary())
    }

    @Test
    fun `pushed work alone is not outstanding, but is still worth saying`() {
        val text = summary(pushed = 102)

        assertTrue(text.startsWith("Nothing outstanding"))
        assertTrue(text.contains("102"))
    }

    @Test
    fun `each kind of outstanding work is counted separately`() {
        assertEquals("5 uncommitted, 2 untracked, 3 to push", summary(uncommitted = 5, untracked = 2, unpushed = 3))
    }

    @Test
    fun `work spread over repositories says how many`() {
        assertTrue(summary(uncommitted = 4, repositories = 3).endsWith("across 3 repositories"))
    }

    @Test
    fun `a single repository is not worth mentioning`() {
        assertEquals("4 uncommitted", summary(uncommitted = 4))
    }
}
