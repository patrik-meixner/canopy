package com.canopy.toolwindow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OldestCommitOfSessionTest {

    private val sessionStart = 2_000L

    @Test
    fun `a branch rebased today keeps its old work out`() {
        val commits = listOf(
            AuthoredCommit("today", 2_500),
            AuthoredCommit("alsoToday", 2_100),
            AuthoredCommit("lastMonth", 1_000),
            AuthoredCommit("olderStill", 500)
        )

        assertEquals("alsoToday", oldestCommitOfSession(commits, sessionStart))
    }

    @Test
    fun `a session that has committed nothing has no start point`() {
        assertNull(oldestCommitOfSession(listOf(AuthoredCommit("old", 1_000)), sessionStart))
    }

    @Test
    fun `an old commit further down does not pull the range back open`() {
        val commits = listOf(
            AuthoredCommit("today", 2_500),
            AuthoredCommit("lastMonth", 1_000),
            AuthoredCommit("recentButRebasedUnder", 2_400)
        )

        assertEquals("today", oldestCommitOfSession(commits, sessionStart))
    }

    @Test
    fun `a log with blank and short lines is read anyway`() {
        val parsed = parseAuthoredCommits("abc 1700\n\ndef\nghi 1600\n")

        assertEquals(listOf(AuthoredCommit("abc", 1700), AuthoredCommit("ghi", 1600)), parsed)
    }
}
