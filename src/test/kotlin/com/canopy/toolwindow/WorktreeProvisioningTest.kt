package com.canopy.toolwindow

import org.junit.Assert.assertEquals
import org.junit.Test

class WorktreeProvisioningTest {

    @Test
    fun `names split on commas and newlines`() {
        assertEquals(listOf(".env", ".envrc", "config/local.yml"), setupFileNames(" .env, .envrc \n config/local.yml "))
    }

    @Test
    fun `a name listed twice is copied once`() {
        assertEquals(listOf(".env"), setupFileNames(".env,.env"))
    }

    @Test
    fun `a file the worktree already has is left alone`() {
        val provisioned = filesToProvision(
            listOf(".env", ".envrc"),
            existsInSource = { true },
            existsInTarget = { it == ".env" }
        )

        assertEquals(listOf(".envrc"), provisioned)
    }

    @Test
    fun `a file the source does not have is skipped`() {
        val provisioned = filesToProvision(
            listOf(".env", "missing.local"),
            existsInSource = { it == ".env" },
            existsInTarget = { false }
        )

        assertEquals(listOf(".env"), provisioned)
    }
}
