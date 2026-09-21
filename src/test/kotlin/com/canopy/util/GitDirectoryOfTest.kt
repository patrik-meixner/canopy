package com.canopy.util

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GitDirectoryOfTest {

    private val home = Files.createTempDirectory("canopy-gitdir")

    private fun repoWith(pointer: String): String {
        val root = Files.createTempDirectory(home, "root")
        Files.writeString(root.resolve(".git"), pointer)
        return root.toString()
    }

    @Test
    fun `a plain repository keeps its own git directory`() {
        val root = Files.createTempDirectory(home, "plain")
        val git = Files.createDirectory(root.resolve(".git"))

        assertEquals(git, gitDirectoryOf(root.toString()))
    }

    @Test
    fun `a worktree points at an absolute directory`() {
        val elsewhere = Files.createDirectory(home.resolve("worktrees-target"))

        assertEquals(elsewhere, gitDirectoryOf(repoWith("gitdir: $elsewhere\n")))
    }

    @Test
    fun `a submodule points relative to its own git file, not to wherever the IDE was started`() {
        val root = Files.createTempDirectory(home, "super")
        val modules = Files.createDirectories(root.resolve("modules").resolve("sub"))
        Files.writeString(root.resolve(".git"), "gitdir: modules/sub\n")

        assertEquals(modules, gitDirectoryOf(root.toString()))
    }

    @Test
    fun `a pointer to nowhere resolves to nothing`() {
        assertNull(gitDirectoryOf(repoWith("gitdir: /no/such/place\n")))
    }
}
