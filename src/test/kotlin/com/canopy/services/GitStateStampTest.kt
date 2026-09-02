package com.canopy.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class GitStateStampTest {

    @Test
    fun `a directory that is no repository has no stamp`() {
        assertEquals(0L, gitStateStamp(Files.createTempDirectory("canopy-plain").toString()))
    }

    @Test
    fun `writing HEAD moves the stamp`() {
        val root = Files.createTempDirectory("canopy-repo")
        val git = Files.createDirectories(root.resolve(".git"))
        Files.writeString(git.resolve("HEAD"), "ref: refs/heads/main\n")
        val before = gitStateStamp(root.toString())

        Files.setLastModifiedTime(git.resolve("HEAD"), java.nio.file.attribute.FileTime.fromMillis(before + 5_000))

        assertTrue(gitStateStamp(root.toString()) > before)
    }

    @Test
    fun `a worktree is read through the directory its git file points at`() {
        val real = Files.createTempDirectory("canopy-real")
        Files.writeString(real.resolve("HEAD"), "ref: refs/heads/feature\n")
        val worktree = Files.createTempDirectory("canopy-worktree")
        Files.writeString(worktree.resolve(".git"), "gitdir: $real\n")

        assertTrue(gitStateStamp(worktree.toString()) > 0L)
    }
}
