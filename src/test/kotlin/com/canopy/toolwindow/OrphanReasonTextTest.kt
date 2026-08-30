package com.canopy.toolwindow

import com.canopy.model.OrphanReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrphanReasonTextTest {

    private fun orphan(reason: OrphanReason, branch: String? = null, repo: String = "canopy") = OrphanWorktree(
        name = "auth-refactor",
        path = "/repo/.worktrees/auth-refactor",
        branch = branch,
        reason = reason,
        repoLabel = repo,
        repoRoot = "/repo"
    )

    @Test
    fun `a worktree that simply has no session shows its branch`() {
        assertTrue(describeReason(orphan(OrphanReason.NoSession, branch = "feature/auth")).endsWith("feature/auth"))
    }

    @Test
    fun `a detached worktree says so rather than showing nothing`() {
        assertTrue(describeReason(orphan(OrphanReason.NoSession)).endsWith("detached HEAD"))
    }

    @Test
    fun `leftover files are named, since they decide whether it is safe to delete`() {
        val text = describeReason(orphan(OrphanReason.LeftoverFiles(listOf("target", ".env"))))

        assertTrue(text.contains("target, .env"))
    }

    @Test
    fun `the repository leads, because it decides which repo the removal runs against`() {
        assertTrue(describeReason(orphan(OrphanReason.EmptyDirectory)).startsWith("canopy"))
    }

    @Test
    fun `no repository label leaves no empty gap`() {
        assertEquals("empty directory, safe to delete", describeReason(orphan(OrphanReason.EmptyDirectory, repo = "")))
    }
}
