package com.canopy.toolwindow

import com.canopy.util.ProcessHelper
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.changes.CurrentContentRevision
import com.intellij.openapi.vcs.changes.Change
import java.nio.file.Path

/**
 * [isYours] separates what is still the reviewer's to act on from what has already left the machine.
 * A pushed file is history: it reads as work in progress otherwise, and there is a lot more of it.
 */
enum class SessionChangeSection(val title: String, val hint: String, val isYours: Boolean) {
    Uncommitted("Changes", "not committed", true),
    Committed("Committed", "not pushed", true),
    Pushed("Pushed", "already on the remote", false),
    Unversioned("Unversioned Files", "not tracked by git", true)
}

/**
 * A working tree split the way review actually proceeds: what is still yours to commit, what you
 * committed but have not shared, what is already on the remote, and what git is not tracking.
 */
data class SessionChangeSet(
    val changes: Map<SessionChangeSection, List<Change>>,
    val unversioned: List<FilePath>,
    /** Pushed work grouped the way it was made: a hundred files are a handful of commits. */
    val pushedCommits: List<CommitWithChanges> = emptyList()
) {
    val isEmpty: Boolean get() = changes.isEmpty() && unversioned.isEmpty()

    /**
     * Content revisions compare by identity, so a recomputed set never equals the old one. Naming
     * what is on screen instead lets an unchanged result leave the tree, its scroll and its
     * selection alone.
     */
    fun signature(): String = buildString {
        for (section in SessionChangeSection.entries) {
            changes[section]?.forEach { append(section.name).append(it.virtualFile?.path ?: it.toString()).append('\n') }
        }
        unversioned.forEach { append("U").append(it.path).append('\n') }
    }
}

object SessionChanges {

    private const val GIT_TIMEOUT_MS = 20_000L

    /**
     * [since] bounds the commit sections to the session's own window. Without it a long-lived team
     * branch reports its entire divergence from the base — thousands of files nobody wrote today.
     */
    fun collect(root: String, since: java.time.Instant?): SessionChangeSet {
        val sharedTip = sharedWithRemote(root)

        return SessionChangeSet(
            changes = mapOf(
                SessionChangeSection.Uncommitted to diff(root, "HEAD", null),
                SessionChangeSection.Committed to unpushedRange(root, sharedTip),
                SessionChangeSection.Pushed to pushedRange(root, sharedTip, since)
            ).filterValues { it.isNotEmpty() },
            unversioned = untracked(root),
            pushedCommits = pushedCommits(root, sharedTip, since)
        )
    }

    /** Newest commit that is both on HEAD and on the remote; everything after it is unpushed. */
    private fun sharedWithRemote(root: String): String? {
        val upstream = upstreamOf(root) ?: return null

        return git(root, "merge-base", "HEAD", upstream)?.trim()?.ifEmpty { null }
    }

    private fun unpushedRange(root: String, sharedTip: String?): List<Change> {
        val from = commitRangeStart(sharedTip, detectBase(root)) ?: return emptyList()

        return diff(root, from, "HEAD")
    }

    /**
     * Where "committed but not shared" starts: the remote's tip when there is one, otherwise the
     * base branch — a branch that was never pushed has all of its commits unshared.
     */
    internal fun commitRangeStart(sharedTip: String?, base: String?): String? = sharedTip ?: base

    /** The same range as [pushedRange], read per commit so the section can be grouped by them. */
    private fun pushedCommits(
        root: String,
        sharedTip: String?,
        since: java.time.Instant?
    ): List<CommitWithChanges> {
        if (sharedTip == null) return emptyList()
        val from = sessionStartPoint(root, since) ?: return emptyList()
        val repository = java.nio.file.Path.of(root).fileName?.toString() ?: root

        return SessionCommits.withChanges(root, repository, "$from..$sharedTip")
    }

    private fun pushedRange(root: String, sharedTip: String?, since: java.time.Instant?): List<Change> {
        if (sharedTip == null) return emptyList()
        val from = sessionStartPoint(root, since) ?: return emptyList()

        return diff(root, from, sharedTip)
    }

    /** The commit the session started from: the parent of its oldest commit inside the window. */
    private fun sessionStartPoint(root: String, since: java.time.Instant?): String? {
        if (since == null) return null
        val commits = git(root, "log", "--since=${since}", "--format=%H", "HEAD") ?: return null
        val oldest = commits.lines().filter { it.isNotBlank() }.lastOrNull() ?: return null

        return git(root, "rev-parse", "--verify", "--quiet", "$oldest^")?.trim()?.ifEmpty { null } ?: oldest
    }

    private fun diff(root: String, from: String, to: String?): List<Change> {
        val range = if (to == null) arrayOf(from) else arrayOf("$from...$to")
        val output = git(root, "diff", "--name-status", "-M", *range) ?: return emptyList()

        return parseNameStatus(output).map { (status, oldPath, newPath) ->
            val filePath = localPath(root, newPath)
            val beforeRevision = if (to == null) "HEAD" else from
            val before = if (status == 'A') null else GitContentRevision(root, oldPath, beforeRevision, filePath)
            val after = if (status == 'D') null else afterRevision(root, newPath, to, filePath)

            Change(before, after)
        }
    }

    /**
     * The working tree is backed by a real file, so the diff's right-hand side is editable: a typo
     * spotted while reviewing gets fixed in place. A committed revision has no file to write to and
     * stays read-only.
     */
    private fun afterRevision(root: String, relativePath: String, to: String?, filePath: FilePath): ContentRevision =
        if (to == null) CurrentContentRevision(filePath) else GitContentRevision(root, relativePath, to, filePath)

    private fun untracked(root: String): List<FilePath> {
        val output = git(root, "ls-files", "--others", "--exclude-standard") ?: return emptyList()

        return output.lines().filter { it.isNotBlank() }.map { localPath(root, it) }
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

    private fun parseNameStatus(output: String): List<Triple<Char, String, String>> =
        output.lines().filter { it.isNotBlank() }.mapNotNull { line ->
            val parts = line.split('\t')
            val code = parts[0].firstOrNull() ?: return@mapNotNull null
            when (code) {
                'R', 'C' -> if (parts.size >= 3) Triple(code, parts[1], parts[2]) else null
                else -> if (parts.size >= 2) Triple(code, parts[1], parts[1]) else null
            }
        }

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
