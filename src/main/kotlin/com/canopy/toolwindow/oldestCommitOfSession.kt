package com.canopy.toolwindow

/** One line of `git log --format=%H %at`: the commit and when its author wrote it. */
data class AuthoredCommit(val hash: String, val authoredAtSeconds: Long)

/**
 * The oldest commit on HEAD that this session actually wrote.
 *
 * Not `git log --since`, which reads the committer date: a rebase stamps every commit on the
 * branch with the moment it was replayed, so a branch rebased onto main during a session reports
 * a month of somebody else's work as the session's own. The author date survives a rebase, which
 * is exactly why it is the one to ask.
 *
 * Walking stops at the first commit authored before the session rather than scanning the whole
 * branch: what is wanted is the run of commits at the tip, not every old commit that happens to
 * carry a recent date.
 */
fun oldestCommitOfSession(commits: List<AuthoredCommit>, sinceSeconds: Long): String? =
    commits.takeWhile { it.authoredAtSeconds >= sinceSeconds }.lastOrNull()?.hash

/** Tolerates the blank and malformed lines a git log can end with. */
fun parseAuthoredCommits(output: String): List<AuthoredCommit> = output.lineSequence()
    .mapNotNull { line ->
        val parts = line.trim().split(' ')
        val at = parts.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null

        AuthoredCommit(parts[0], at)
    }
    .toList()
