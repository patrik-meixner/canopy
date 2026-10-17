package com.canopy.util

import kotlin.test.Test
import kotlin.test.assertEquals

class GitCommandTest {

    @Test
    fun `reading state never takes the index lock a commit needs`() {
        assertEquals(
            listOf("git", "--no-optional-locks", "-C", "/repo", "status", "--porcelain"),
            gitCommand("/repo", "status", "--porcelain").toList()
        )
    }

    @Test
    fun `the flag goes before the subcommand, where git accepts it`() {
        val argv = gitCommand("/repo", "diff", "--raw").toList()

        assertEquals("--no-optional-locks", argv[1])
        assertEquals("diff", argv[4])
    }

    @Test
    fun `a subcommand that takes no arguments still names the repository`() {
        assertEquals(
            listOf("git", "--no-optional-locks", "-C", "/repo", "fetch"),
            gitCommand("/repo", "fetch").toList()
        )
    }
}
