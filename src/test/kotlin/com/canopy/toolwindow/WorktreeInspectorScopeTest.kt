package com.canopy.toolwindow

import com.canopy.model.RepoScope
import kotlin.test.Test
import kotlin.test.assertEquals

class WorktreeInspectorScopeTest {

    private val workspace = "/ws/rdm-agentic-workspace"

    private fun entry(path: String) = WorktreeEntry(path, branch = null, prunable = false, prunableReason = null)

    @Test
    fun `drops the submodule main worktree that git reports as the git directory`() {
        val scope = RepoScope(
            label = "dmm",
            root = "$workspace/dmm",
            gitCommonDir = "$workspace/.git/modules/dmm",
            isSubmodule = true
        )
        val entries = listOf(
            entry("$workspace/.git/modules/dmm"),
            entry("$workspace/dmm/.worktrees/ONE-97893-review")
        )

        val kept = WorktreeInspector.secondaryWorktrees(entries, scope)

        assertEquals(listOf("$workspace/dmm/.worktrees/ONE-97893-review"), kept.map { it.path })
    }

    @Test
    fun `drops the superproject checkout itself`() {
        val scope = RepoScope(
            label = "rdm-agentic-workspace",
            root = workspace,
            gitCommonDir = "$workspace/.git",
            isSubmodule = false
        )
        val entries = listOf(
            entry(workspace),
            entry("$workspace/.claude/worktrees/bim-embed-deploy"),
            entry("$workspace/.worktrees/decathlon-enectiva")
        )

        val kept = WorktreeInspector.secondaryWorktrees(entries, scope)

        assertEquals(
            listOf("$workspace/.claude/worktrees/bim-embed-deploy", "$workspace/.worktrees/decathlon-enectiva"),
            kept.map { it.path }
        )
    }

    @Test
    fun `matches paths that differ only by trailing separators`() {
        val scope = RepoScope(
            label = "dmm",
            root = "$workspace/dmm/",
            gitCommonDir = "$workspace/.git/modules/./dmm",
            isSubmodule = true
        )
        val entries = listOf(
            entry("$workspace/dmm"),
            entry("$workspace/.git/modules/dmm"),
            entry("$workspace/dmm/.worktrees/ONE-97893-review")
        )

        val kept = WorktreeInspector.secondaryWorktrees(entries, scope)

        assertEquals(listOf("$workspace/dmm/.worktrees/ONE-97893-review"), kept.map { it.path })
    }
}
