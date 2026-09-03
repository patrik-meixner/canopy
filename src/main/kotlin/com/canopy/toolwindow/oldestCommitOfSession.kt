package com.canopy.toolwindow

data class AuthoredCommit(val hash: String, val authoredAtSeconds: Long)

/** Author date, not `git log --since`: a rebase restamps every committer date with the replay. */
fun oldestCommitOfSession(commits: List<AuthoredCommit>, sinceSeconds: Long): String? =
    commits.takeWhile { it.authoredAtSeconds >= sinceSeconds }.lastOrNull()?.hash

fun parseAuthoredCommits(output: String): List<AuthoredCommit> = output.lineSequence()
    .mapNotNull { line ->
        val parts = line.trim().split(' ')
        val at = parts.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null

        AuthoredCommit(parts[0], at)
    }
    .toList()
