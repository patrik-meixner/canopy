package com.canopy.toolwindow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorktreeCleanupTest {

    private fun entry(name: String, branch: String? = "feature/$name") =
        WorktreeEntry("/repo/.worktrees/$name", branch, prunable = false, prunableReason = null)

    private fun status(
        name: String,
        ahead: Int = 0,
        dirty: List<String> = emptyList(),
        state: WorktreeState = WorktreeState.OK
    ) = WorktreeStatus(state = state, name = name, wtBranch = "feature/$name", mainBranch = "main", ahead = ahead, dirtyFiles = dirty)

    @Test
    fun `a merged, clean worktree with no session can go`() {
        val found = cleanupCandidates(listOf(entry("done")), { status(it) }, emptySet())

        assertEquals(listOf("done"), found.map { it.name })
        assertTrue(found.single().reason.contains("merged into main"))
    }

    @Test
    fun `a worktree with unmerged commits is left alone`() {
        assertEquals(emptyList<CleanupCandidate>(), cleanupCandidates(listOf(entry("wip")), { status(it, ahead = 2) }, emptySet()))
    }

    @Test
    fun `uncommitted work protects a worktree, however merged its branch is`() {
        val found = cleanupCandidates(listOf(entry("dirty")), { status(it, dirty = listOf("App.kt")) }, emptySet())

        assertEquals(emptyList<CleanupCandidate>(), found)
    }

    @Test
    fun `a worktree a session is using is never a candidate`() {
        assertEquals(emptyList<CleanupCandidate>(), cleanupCandidates(listOf(entry("busy")), { status(it) }, setOf("busy")))
    }

    @Test
    fun `a worktree mid-rebase is not something to delete underneath`() {
        val found = cleanupCandidates(listOf(entry("stuck")), { status(it, state = WorktreeState.REBASING) }, emptySet())

        assertEquals(emptyList<CleanupCandidate>(), found)
    }

    @Test
    fun `a worktree whose state was never computed is left alone rather than assumed safe`() {
        assertEquals(emptyList<CleanupCandidate>(), cleanupCandidates(listOf(entry("unknown")), { null }, emptySet()))
    }
}
