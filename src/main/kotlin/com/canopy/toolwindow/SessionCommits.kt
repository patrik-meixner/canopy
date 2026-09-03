package com.canopy.toolwindow

import com.canopy.util.ProcessHelper
import com.intellij.openapi.vcs.changes.Change
import java.time.Instant

data class CommitWithChanges(val commit: CommitEntry, val changes: List<Change>)

data class CommitEntry(
    val root: String,
    val repository: String,
    val sha: String,
    val subject: String,
    val author: String,
    val atMillis: Long
)

object SessionCommits {

    private const val GIT_TIMEOUT_MS = 30_000L
    private const val FORMAT = "%H%x1f%s%x1f%an%x1f%at"
    private const val LIMIT = 200

    fun list(root: String, repository: String, since: Instant?): List<CommitEntry> {
        val args = mutableListOf("log", "--format=$FORMAT", "--max-count=$LIMIT", "--no-merges")
        since?.let { args.add("--since=$it") }
        val output = git(root, *args.toTypedArray()) ?: return emptyList()

        return parseCommits(output, root, repository)
    }

    fun withChanges(root: String, repository: String, range: String): List<CommitWithChanges> {
        val output = git(
            root,
            "log",
            "--format=%x01%H%x1f%s%x1f%an%x1f%at",
            "--name-status",
            "-M",
            "--no-merges",
            "--max-count=$LIMIT",
            range
        ) ?: return emptyList()

        return parseCommitsWithFiles(output, root, repository).map { (commit, files) ->
            CommitWithChanges(
                commit,
                files.map { (status, oldPath, newPath) -> changeFor(root, commit.sha, status, oldPath, newPath) }
            )
        }
    }

    internal fun changeFor(root: String, sha: String, status: Char, oldPath: String, newPath: String): Change {
        val before = if (status == 'A') null else GitContentRevision(root, oldPath, "$sha^", localPathIn(root, oldPath))
        val after = if (status == 'D') null else GitContentRevision(root, newPath, sha, localPathIn(root, newPath))

        return Change(before, after)
    }

    fun changesIn(root: String, sha: String): List<Change> {
        val output = git(root, "show", "--name-status", "-M", "--format=", sha) ?: return emptyList()

        return parseNameStatusLines(output).map { (status, oldPath, newPath) ->
            val before = if (status == 'A') null else GitContentRevision(root, oldPath, "$sha^", localPathIn(root, oldPath))
            val after = if (status == 'D') null else GitContentRevision(root, newPath, sha, localPathIn(root, newPath))

            Change(before, after)
        }
    }

    private fun localPathIn(root: String, relative: String) =
        com.intellij.openapi.vcs.LocalFilePath(java.nio.file.Path.of(root, relative).toString(), false)

    private fun git(root: String, vararg args: String): String? = try {
        val result = ProcessHelper.execWithTimeout(
            arrayOf("git", "-C", root, *args),
            GIT_TIMEOUT_MS,
            extraEnv = mapOf("GIT_OPTIONAL_LOCKS" to "0")
        )
        if (result.exitCode == 0) result.output else null
    } catch (_: Exception) {
        null
    }
}

internal fun parseCommits(output: String, root: String, repository: String): List<CommitEntry> =
    output.lineSequence()
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val parts = line.split('\u001F')
            if (parts.size < 4) return@mapNotNull null

            CommitEntry(
                root = root,
                repository = repository,
                sha = parts[0],
                subject = parts[1],
                author = parts[2],
                atMillis = (parts[3].toLongOrNull() ?: 0L) * 1000
            )
        }
        .toList()

internal fun parseNameStatusLines(output: String): List<Triple<Char, String, String>> =
    output.lines().filter { it.isNotBlank() }.mapNotNull { line ->
        val parts = line.split('\t')
        val code = parts[0].firstOrNull() ?: return@mapNotNull null

        when (code) {
            'R', 'C' -> if (parts.size >= 3) Triple(code, parts[1], parts[2]) else null
            else -> if (parts.size >= 2) Triple(code, parts[1], parts[1]) else null
        }
    }

private const val COMMIT_MARKER = '\u0001'

internal fun parseCommitsWithFiles(
    output: String,
    root: String,
    repository: String
): List<Pair<CommitEntry, List<Triple<Char, String, String>>>> {
    val result = mutableListOf<Pair<CommitEntry, MutableList<Triple<Char, String, String>>>>()

    for (line in output.lineSequence()) {
        if (line.isBlank()) continue
        if (line.first() == COMMIT_MARKER) {
            val commit = parseCommits(line.drop(1), root, repository).firstOrNull() ?: continue
            result.add(commit to mutableListOf())
            continue
        }

        val current = result.lastOrNull() ?: continue
        parseNameStatusLines(line).firstOrNull()?.let { current.second.add(it) }
    }

    return result.map { (commit, files) -> commit to files.toList() }
}
