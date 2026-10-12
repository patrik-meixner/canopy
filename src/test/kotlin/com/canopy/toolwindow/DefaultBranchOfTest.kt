package com.canopy.toolwindow

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DefaultBranchOfTest {

    @Test
    fun `the remote's own HEAD names the default branch`() {
        assertEquals("origin/develop", defaultBranchOf("origin/develop") { true })
    }

    @Test
    fun `without it, origin's main comes before a local one`() {
        assertEquals("origin/main", defaultBranchOf(null) { it in setOf("origin/main", "main") })
    }

    @Test
    fun `master is found when there is no main`() {
        assertEquals("origin/master", defaultBranchOf(null) { it == "origin/master" })
        assertEquals("master", defaultBranchOf(null) { it == "master" })
    }

    @Test
    fun `a repository with none of them has no default branch`() {
        assertNull(defaultBranchOf(null) { false })
    }
}
