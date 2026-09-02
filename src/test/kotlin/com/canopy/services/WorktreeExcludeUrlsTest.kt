package com.canopy.services

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class WorktreeExcludeUrlsTest {

    private fun checkout(root: Path, at: String, gitdir: String): Path {
        val directory = Files.createDirectories(root.resolve(at))
        Files.writeString(directory.resolve(".git"), "gitdir: $gitdir\n")

        return directory
    }

    @Test
    fun `a worktree is excluded wherever it was put`() {
        val root = Files.createTempDirectory("canopy-repo")
        checkout(root, ".claude/worktrees/agent-a", "$root/.git/worktrees/agent-a")
        checkout(root, ".worktrees/feature-b", "$root/.git/worktrees/feature-b")

        val excluded = worktreeExcludeUrls(root.toString())

        assertEquals(
            listOf("file://$root/.claude/worktrees/agent-a", "file://$root/.worktrees/feature-b"),
            excluded.sorted()
        )
    }

    @Test
    fun `a worktree of a submodule is excluded too`() {
        val root = Files.createTempDirectory("canopy-sub-wt")
        checkout(root, "dmm/.worktrees/review", "$root/.git/modules/dmm/worktrees/review")

        assertEquals(listOf("file://$root/dmm/.worktrees/review"), worktreeExcludeUrls(root.toString()))
    }

    @Test
    fun `a submodule is left alone`() {
        val root = Files.createTempDirectory("canopy-super")
        checkout(root, "libs/shared", "$root/.git/modules/libs/shared")

        assertEquals(emptyList<String>(), worktreeExcludeUrls(root.toString()))
    }

    @Test
    fun `an ordinary directory named worktrees is not one`() {
        val root = Files.createTempDirectory("canopy-plain")
        Files.createDirectories(root.resolve("worktrees").resolve("notes"))

        assertEquals(emptyList<String>(), worktreeExcludeUrls(root.toString()))
    }

    @Test
    fun `the repository itself is never excluded`() {
        val root = Files.createTempDirectory("canopy-main")
        Files.createDirectories(root.resolve(".git").resolve("worktrees"))

        assertEquals(emptyList<String>(), worktreeExcludeUrls(root.toString()))
    }

    @Test
    fun `the real project excludes the worktrees it actually has`() {
        val real = "/Users/patrik.meixner/WebstormProjects/own/urbido-2.0"
        if (!Files.isDirectory(Path.of(real))) return

        println("REAL -> " + worktreeExcludeUrls(real).joinToString("\n         "))
    }
}
