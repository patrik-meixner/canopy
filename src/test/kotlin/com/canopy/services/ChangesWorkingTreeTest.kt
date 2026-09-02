package com.canopy.services

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChangesWorkingTreeTest {

    private val claudeHome = "/Users/p/.claude/"

    private fun changes(path: String) = changesWorkingTree(path, claudeHome)

    @Test
    fun `a source file is worth a refresh`() {
        assertTrue(changes("/ws/canopy/src/main/kotlin/App.kt"))
    }

    @Test
    fun `what a build wrote is not`() {
        assertFalse(changes("/ws/canopy/build/classes/kotlin/main/App.class"))
        assertFalse(changes("/ws/one-frontend/node_modules/react/index.js"))
        assertFalse(changes("/ws/dmm/target/surefire-reports/x.txt"))
    }

    @Test
    fun `git's own bookkeeping is not, but a commit still is`() {
        assertFalse(changes("/ws/canopy/.git/objects/ab/cdef"))
        assertTrue(changes("/ws/canopy/.git/HEAD"))
        assertTrue(changes("/ws/canopy/.git/index"))
    }

    @Test
    fun `a directory merely named build inside a source path is still a build directory`() {
        assertFalse(changes("/ws/canopy/build/tmp/x"))
    }

    @Test
    fun `what the agent writes about itself is not a change to review`() {
        assertFalse(changes("/Users/p/.claude/projects/-Users-p-ws-canopy/0f3a.jsonl"))
        assertFalse(changes("/Users/p/.claude/sessions/14466.json"))
        assertFalse(changes("/Users/p/.claude/tasks/0f3a/1.json"))
    }

    @Test
    fun `a worktree the agent made under the project's own dot-claude is still reviewed`() {
        assertTrue(changes("/ws/canopy/.claude/worktrees/agent-a2d6/src/App.kt"))
    }
}
