package com.canopy.toolwindow

import org.junit.Assert.assertTrue
import org.junit.Test

class BranchNoteTest {

    private fun branch(
        worktree: String? = null,
        ahead: Int = 0,
        behind: Int = 0,
        gone: Boolean = false,
        upstream: String? = "origin/x"
    ) = BranchEntry("x", upstream, gone, ahead, behind, System.currentTimeMillis(), false, worktree)

    @Test
    fun `a branch already checked out names the worktree holding it`() {
        assertTrue(branchNote(branch(worktree = "/repo/.worktrees/auth")).contains("in auth"))
    }

    @Test
    fun `divergence shows both directions`() {
        val note = branchNote(branch(ahead = 3, behind = 12))

        assertTrue(note.contains("↑3"))
        assertTrue(note.contains("↓12"))
    }

    @Test
    fun `a branch whose remote is gone says so`() {
        assertTrue(branchNote(branch(gone = true)).contains("remote gone"))
    }

    @Test
    fun `a branch that was never pushed is not called gone`() {
        val note = branchNote(branch(upstream = null))

        assertTrue(note.contains("never pushed"))
        assertTrue(!note.contains("remote gone"))
    }
}
