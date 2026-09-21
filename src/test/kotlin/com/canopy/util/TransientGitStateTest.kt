package com.canopy.util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransientGitStateTest {

    @Test
    fun `a settled repository is not mid-operation`() {
        assertFalse(isTransientGitState(setOf("HEAD", "config", "index", "refs", "objects")))
    }

    @Test
    fun `a conflicted merge is mid-operation`() {
        assertTrue(isTransientGitState(setOf("HEAD", "MERGE_HEAD")))
    }

    @Test
    fun `a rebase is mid-operation while it still has its directory`() {
        assertTrue(isTransientGitState(setOf("HEAD", "rebase-merge")))
        assertTrue(isTransientGitState(setOf("HEAD", "rebase-apply")))
    }

    @Test
    fun `a cherry-pick and a revert count too`() {
        assertTrue(isTransientGitState(setOf("CHERRY_PICK_HEAD")))
        assertTrue(isTransientGitState(setOf("REVERT_HEAD")))
    }

    @Test
    fun `a branch whose name merely resembles one of them is not an operation`() {
        assertFalse(isTransientGitState(setOf("MERGE_HEAD_backup", "rebase-merge.bak", "ORIG_HEAD")))
    }
}
