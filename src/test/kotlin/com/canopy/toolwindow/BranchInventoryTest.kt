package com.canopy.toolwindow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BranchInventoryTest {

    @Test
    fun `the checked out branch is marked`() {
        val branches = parseBranches("main|origin/main||1788119971|*\nother|origin/other||1788119000| ")

        assertTrue(branches.first { it.name == "main" }.isCurrent)
        assertFalse(branches.first { it.name == "other" }.isCurrent)
    }

    @Test
    fun `a branch whose remote is gone is called out`() {
        val branches = parseBranches("stale|origin/stale|[gone]|1777276167| ")

        assertTrue(branches.single().isGone)
    }

    @Test
    fun `divergence is read off the track field`() {
        val branches = parseBranches("work|origin/work|[ahead 3, behind 12]|1777276167| ")

        assertEquals(3, branches.single().ahead)
        assertEquals(12, branches.single().behind)
    }

    @Test
    fun `a branch with no upstream is not reported as gone`() {
        val branches = parseBranches("local-only|||1777276167| ")

        assertFalse(branches.single().isGone)
        assertEquals(null, branches.single().upstream)
    }

    @Test
    fun `the commit date arrives in milliseconds`() {
        assertEquals(1777276167000L, parseBranches("b|||1777276167| ").single().committedAtMillis)
    }

    @Test
    fun `a malformed line is skipped rather than breaking the list`() {
        val branches = parseBranches("good|||1777276167| \ngarbage")

        assertEquals(listOf("good"), branches.map { it.name })
    }
}
