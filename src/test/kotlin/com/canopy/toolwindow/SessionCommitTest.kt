package com.canopy.toolwindow

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionCommitTest {

    @Test
    fun `a submodule counts as inside the superproject`() {
        assertTrue(SessionCommit.isInside("/ws/dmm", "/ws"))
    }

    @Test
    fun `the superproject is not inside itself`() {
        assertFalse(SessionCommit.isInside("/ws", "/ws"))
    }

    @Test
    fun `a repository elsewhere on disk is not inside`() {
        assertFalse(SessionCommit.isInside("/other/repo", "/ws"))
    }

    @Test
    fun `a sibling with a shared prefix is not inside`() {
        assertFalse(SessionCommit.isInside("/workspace-two", "/ws"))
    }

    @Test
    fun `without a superproject nothing is inside`() {
        assertFalse(SessionCommit.isInside("/ws/dmm", null))
    }
}
