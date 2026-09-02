package com.canopy.services

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class WorktreeExcludeUrlsTest {

    @Test
    fun `the worktrees directory of the root is excluded`() {
        val root = Files.createTempDirectory("canopy-root")
        Files.createDirectories(root.resolve(".claude").resolve("worktrees"))

        assertEquals(listOf("file://$root/.claude/worktrees"), worktreeExcludeUrls(root.toString()))
    }

    @Test
    fun `a submodule's own worktrees are excluded too`() {
        val root = Files.createTempDirectory("canopy-super")
        Files.createDirectories(root.resolve("sub").resolve(".claude").resolve("worktrees"))

        assertEquals(listOf("file://$root/sub/.claude/worktrees"), worktreeExcludeUrls(root.toString()))
    }

    @Test
    fun `a claude directory without worktrees excludes nothing`() {
        val root = Files.createTempDirectory("canopy-bare")
        Files.createDirectories(root.resolve(".claude").resolve("agents"))

        assertEquals(emptyList<String>(), worktreeExcludeUrls(root.toString()))
    }

    @Test
    fun `the real project reports exactly one worktrees directory`() {
        val real = "/Users/patrik.meixner/WebstormProjects/own/urbido-2.0"
        if (!Files.isDirectory(Path.of(real, ".claude", "worktrees"))) return

        println("REAL -> " + worktreeExcludeUrls(real))
        assertEquals(1, worktreeExcludeUrls(real).size)
    }
}
