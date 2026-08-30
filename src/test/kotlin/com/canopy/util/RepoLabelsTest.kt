package com.canopy.util

import com.canopy.model.RepoScope
import kotlin.test.Test
import kotlin.test.assertEquals

class RepoLabelsTest {

    private val scopes = listOf(
        RepoScope("workspace", "/ws", "/ws/.git", isSubmodule = false),
        RepoScope("dmm", "/ws/dmm", "/ws/.git/modules/dmm", isSubmodule = true),
        RepoScope("one-frontend", "/ws/one-frontend", "/ws/.git/modules/one-frontend", isSubmodule = true)
    )

    @Test
    fun `attributes a submodule edit to the submodule, not the superproject`() {
        val paths = mapOf("/ws/dmm/src/Main.kt" to 1)

        assertEquals(listOf("dmm"), repoLabelsFor(paths, scopes))
    }

    @Test
    fun `orders repos by how many files were written`() {
        val paths = mapOf("/ws/one-frontend" to 2, "/ws/dmm" to 1)

        assertEquals(listOf("one-frontend", "dmm"), repoLabelsFor(paths, scopes))
    }

    @Test
    fun `keeps superproject files under the superproject`() {
        val paths = mapOf("/ws/README.md" to 1)

        assertEquals(listOf("workspace"), repoLabelsFor(paths, scopes))
    }

    @Test
    fun `ignores paths outside every repo`() {
        val paths = mapOf("/elsewhere/file.txt" to 1)

        assertEquals(emptyList(), repoLabelsFor(paths, scopes))
    }
}
