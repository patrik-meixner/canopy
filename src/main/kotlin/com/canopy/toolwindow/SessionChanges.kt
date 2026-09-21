package com.canopy.toolwindow

import com.canopy.util.ProcessHelper
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.changes.Change
import java.nio.file.Path

enum class SessionChangeSection(val title: String, val isYours: Boolean) {
    Uncommitted("Changes", true),
    Elsewhere("Elsewhere in this repository", false),
    Committed("Committed", true),
    Pushed("Pushed", false),
    Unversioned("Unversioned Files", true)
}

data class SessionChangeSet(
    val changes: Map<SessionChangeSection, List<Change>>,
    val unversioned: List<FilePath>,
    val pushedCommits: List<CommitWithChanges> = emptyList(),
    val isEstimated: Boolean = false,
    val midGitOperation: Boolean = false,
    val notes: Map<Change, String> = emptyMap()
) {
    val isEmpty: Boolean get() = changes.isEmpty() && unversioned.isEmpty()

    fun signature(): Long = pathSignature(
        sequence {
            for (section in SessionChangeSection.entries) {
                changes[section]?.forEach { yield("${'$'}{section.ordinal}:${'$'}{pathOf(it)}") }
            }
            unversioned.forEach { yield("U:${'$'}{it.path}") }
        }
    )

    /** Never Change.toString(): it formats both revisions, for every file, on every comparison. */
    private fun pathOf(change: Change): String =
        change.afterRevision?.file?.path ?: change.beforeRevision?.file?.path.orEmpty()
}

private fun pathOfChange(change: Change): String =
    change.afterRevision?.file?.path ?: change.beforeRevision?.file?.path.orEmpty()

object SessionChanges {

    private const val GIT_TIMEOUT_MS = 20_000L

    private const val SESSION_COMMIT_LIMIT = 200

    fun collect(
        root: String,
        since: java.time.Instant?,
        isOwn: (String) -> Boolean = { true },
        showElsewhere: Boolean = true,
        ownCommits: Set<String>? = null
    ): SessionChangeSet {
        if (isMidGitOperation(root)) return SessionChangeSet(emptyMap(), emptyList(), midGitOperation = true)


        val sharedTip = sharedWithRemote(root)
        val sessionStart = sessionStartPoint(root, since)
        val repository = java.nio.file.Path.of(root).fileName?.toString() ?: root
        val uncommitted = noted(root, "HEAD", null)
        val (own, elsewhere) = uncommitted.map { it.first }.partition { isOwn(pathOfChange(it)) }
        val notes = uncommitted.mapNotNull { (change, note) -> note?.let { change to it } }.toMap()

        val unpushedFrom = unpushedStart(root, sharedTip, sessionStart)
        val committed = unpushedFrom?.let { diff(root, it, "HEAD") }.orEmpty()
        val unpushedCommits = if (ownCommits == null || unpushedFrom == null) emptyList() else SessionCommits.withChanges(root, repository, "$unpushedFrom..HEAD")

        val pushedRange = if (sharedTip != null && sessionStart != null) sessionStart to sharedTip else null
        val pushed = pushedRange?.let { (from, to) -> diff(root, from, to) }.orEmpty()
        val pushedCommits = pushedRange?.let { (from, to) -> SessionCommits.withChanges(root, repository, "$from..$to") }.orEmpty()

        return SessionChangeSet(
            notes = notes,
            changes = mapOf(
                SessionChangeSection.Uncommitted to own,
                SessionChangeSection.Elsewhere to if (showElsewhere) elsewhere else emptyList(),
                SessionChangeSection.Committed to touchedByOwnCommits(committed, unpushedCommits, ownCommits),
                SessionChangeSection.Pushed to touchedByOwnCommits(pushed, pushedCommits, ownCommits)
            ).filterValues { it.isNotEmpty() },
            unversioned = untracked(root).filter { isOwn(it.path) },
            pushedCommits = pushedCommits.filter { ownCommits == null || it.commit.sha in ownCommits }
        )
    }

    private fun touchedByOwnCommits(
        changes: List<Change>,
        commits: List<CommitWithChanges>,
        ownCommits: Set<String>?
    ): List<Change> {
        if (ownCommits == null) return changes
        val touched = commits.filter { it.commit.sha in ownCommits }.flatMap { it.changes }.mapTo(HashSet(), ::pathOfChange)

        return changes.filter { pathOfChange(it) in touched }
    }

    private fun sharedWithRemote(root: String): String? {
        val upstream = upstreamOf(root) ?: return null

        return git(root, "merge-base", "HEAD", upstream)?.trim()?.ifEmpty { null }
    }

    private fun unpushedStart(root: String, sharedTip: String?, sessionStart: String?): String? {
        if (sessionStart == null) return null
        val from = commitRangeStart(sharedTip, detectBase(root)) ?: return null

        return if (isAncestor(root, from, sessionStart)) sessionStart else from
    }

    private fun isAncestor(root: String, ancestor: String, descendant: String): Boolean =
        git(root, "merge-base", "--is-ancestor", ancestor, descendant) != null

    internal fun commitRangeStart(sharedTip: String?, base: String?): String? = sharedTip ?: base

    private fun isMidGitOperation(root: String): Boolean {
        val directory = com.canopy.util.gitDirectoryOf(root) ?: return false
        val entries = runCatching { java.nio.file.Files.list(directory).use { it.map { path -> path.fileName.toString() }.toList() } }
            .getOrNull()?.toSet() ?: return false

        return com.canopy.util.isTransientGitState(entries)
    }

    private fun sessionStartPoint(root: String, since: java.time.Instant?): String? {
        if (since == null) return null
        val log = git(root, "log", "--format=%H %at", "-n", "$SESSION_COMMIT_LIMIT", "HEAD") ?: return null
        val oldest = oldestCommitOfSession(parseAuthoredCommits(log), since.epochSecond) ?: return null

        return git(root, "rev-parse", "--verify", "--quiet", "$oldest^")?.trim()?.ifEmpty { null } ?: oldest
    }

    private fun diff(root: String, from: String, to: String?): List<Change> = noted(root, from, to).map { it.first }

    /** Each change with what git says about it, for the rows whose diff will show nothing. */
    private fun noted(root: String, from: String, to: String?): List<Pair<Change, String?>> {
        val range = if (to == null) arrayOf(from) else arrayOf("$from...$to")
        val output = git(root, "diff", "--raw", "--numstat", "-M", *range) ?: return emptyList()

        return parseGitDiffEntries(output).map { entry ->
            val filePath = localPath(root, entry.newPath)
            val beforeRevision = if (to == null) "HEAD" else from
            val before = if (entry.status == 'A') null
            else GitContentRevision(root, entry.oldPath, beforeRevision, filePath)
            val after = if (entry.status == 'D') null else afterRevision(root, entry.newPath, to, filePath)

            Change(before, after) to changeNote(entry)
        }
    }

    private fun afterRevision(root: String, relativePath: String, to: String?, filePath: FilePath): ContentRevision =
        if (to == null) WorkingCopyRevision(root, relativePath, "HEAD", filePath)
        else GitContentRevision(root, relativePath, to, filePath)

    private fun untracked(root: String): List<FilePath> {
        val output = git(root, "ls-files", "--others", "--exclude-standard") ?: return emptyList()

        return output.lines()
            .filter { it.isNotBlank() && !isOperatingSystemJunk(it) }
            .map { localPath(root, it) }
    }

    private fun upstreamOf(root: String): String? =
        git(root, "rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{upstream}")?.trim()?.ifEmpty { null }

    private fun detectBase(root: String): String? {
        for (branch in listOf("main", "master")) {
            if (git(root, "rev-parse", "--verify", "--quiet", "refs/heads/$branch") != null) return branch
        }

        return git(root, "symbolic-ref", "--short", "refs/remotes/origin/HEAD")?.trim()?.ifEmpty { null }
    }

    private fun localPath(root: String, relative: String): FilePath =
        LocalFilePath(Path.of(root, relative).toString(), false)

    private fun git(root: String, vararg args: String): String? = try {
        val result = ProcessHelper.execWithTimeout(
            command = arrayOf("git", "-C", root, *args),
            timeoutMs = GIT_TIMEOUT_MS,
            extraEnv = mapOf("GIT_OPTIONAL_LOCKS" to "0")
        )
        if (result.exitCode == 0) result.output else null
    } catch (_: Exception) {
        null
    }
}
