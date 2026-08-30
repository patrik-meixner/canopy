package com.canopy.util

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class GitWorkingTreeRootsTest {

    private val gitEntries = setOf(
        "/ws/.git",
        "/ws/dmm/.git",
        "/ws/one-frontend/.worktrees/ONE-97893-fe/.git"
    )

    private val hasGitEntry: (Path) -> Boolean = { it.toString() in gitEntries }

    @Test
    fun `attributes a submodule file to the submodule, not the superproject`() {
        val roots = gitWorkingTreeRoots(setOf("/ws/dmm/src/Main.kt"), hasGitEntry)

        assertEquals(setOf("/ws/dmm"), roots.keys)
    }

    @Test
    fun `finds the worktree rather than the repo it belongs to`() {
        val roots = gitWorkingTreeRoots(setOf("/ws/one-frontend/.worktrees/ONE-97893-fe/app.tsx"), hasGitEntry)

        assertEquals(setOf("/ws/one-frontend/.worktrees/ONE-97893-fe"), roots.keys)
    }

    @Test
    fun `collects every distinct root a session wrote to`() {
        val roots = gitWorkingTreeRoots(
            setOf(
                "/ws/README.md",
                "/ws/dmm/src/Main.kt",
                "/ws/dmm/src/Other.kt",
                "/ws/one-frontend/.worktrees/ONE-97893-fe/app.tsx"
            ),
            hasGitEntry
        )

        assertEquals(
            mapOf("/ws" to 1, "/ws/dmm" to 2, "/ws/one-frontend/.worktrees/ONE-97893-fe" to 1),
            roots
        )
    }

    @Test
    fun `ignores files that live in no repository`() {
        val roots = gitWorkingTreeRoots(setOf("/elsewhere/notes.txt"), hasGitEntry)

        assertEquals(emptyMap(), roots)
    }
}
