package com.canopy.services

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MayMoveWorktreesTest {

    private fun moves(path: String, isDirectory: Boolean = false) = mayMoveWorktrees(path, isDirectory, "/ws")

    @Test
    fun `a git file appearing or vanishing may add or drop a worktree`() {
        assertTrue(moves("/ws/.claude/worktrees/agent-a2d6/.git"))
        assertTrue(moves("/ws/dmm/.worktrees/ONE-1/.git"))
    }

    @Test
    fun `a new directory near the top may be a worktree whose git file the IDE never saw`() {
        assertTrue(moves("/ws/.claude/worktrees/agent-a2d6", isDirectory = true))
    }

    @Test
    fun `an ordinary file does not`() {
        assertFalse(moves("/ws/src/App.kt"))
    }

    @Test
    fun `a directory deeper than the search does not`() {
        assertFalse(moves("/ws/a/b/c/d/e", isDirectory = true))
    }

    @Test
    fun `build output and git internals do not`() {
        assertFalse(moves("/ws/node_modules/react", isDirectory = true))
        assertFalse(moves("/ws/build", isDirectory = true))
        assertFalse(moves("/ws/.git/modules/dmm", isDirectory = true))
    }

    @Test
    fun `anything outside the project does not`() {
        assertFalse(moves("/elsewhere/.git"))
        assertFalse(moves("/wsx/.git"))
    }
}
