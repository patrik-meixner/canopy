package com.canopy.services

import java.nio.file.Files
import java.nio.file.Path

private const val WORKTREES_DIRECTORY = "worktrees"

/**
 * Every worktree directory under a set of repository roots, as VFS urls.
 *
 * A worktree is a second checkout of code that is already indexed once, so indexing it buys
 * nothing and costs a full copy of the repository: twelve of them outnumbered the project itself
 * four to one. Nobody edits in them either - the agent does, and the diff is read from git.
 */
fun worktreeExcludeUrls(repositoryRoots: List<String>): List<String> = repositoryRoots
    .map { Path.of(it, ".claude", WORKTREES_DIRECTORY) }
    .filter { Files.isDirectory(it) }
    .map { "file://${it.toAbsolutePath()}" }
