package com.canopy.toolwindow

import com.canopy.util.ProcessHelper
import java.nio.file.Path

data class CommitOutcome(val root: String, val ok: Boolean, val detail: String)

/**
 * Commits a session's work across every repository it touched.
 *
 * A submodule commit alone leaves the superproject pointing at the old revision, so the pointer is
 * staged and committed in the parent too — otherwise the change is invisible to anyone who clones.
 */
object SessionCommit {

    private const val GIT_TIMEOUT_MS = 60_000L

    fun commit(roots: List<String>, message: String, superproject: String?): List<CommitOutcome> =
        finish(roots.mapNotNull { root -> commitOne(root, message)?.let { root to it } }, message, superproject)

    /**
     * Commits only the files that were picked, leaving the rest of the working tree where it is.
     *
     * The pathspec is repeated on the commit so anything already staged for other reasons does not
     * ride along in a commit that claims to be about the selection.
     */
    fun commitPaths(
        pathsByRoot: Map<String, List<String>>,
        message: String,
        superproject: String?
    ): List<CommitOutcome> =
        finish(
            pathsByRoot.mapNotNull { (root, paths) -> commitSome(root, paths, message)?.let { root to it } },
            message,
            superproject
        )

    private fun finish(
        committed: List<Pair<String, CommitOutcome>>,
        message: String,
        superproject: String?
    ): List<CommitOutcome> {
        val outcomes = committed.map { it.second }
        val submodules = committed.map { it.first }.filter { isInside(it, superproject) }

        if (submodules.isEmpty() || superproject == null) return outcomes

        return outcomes + updatePointers(superproject, submodules, message)
    }

    private fun commitSome(root: String, paths: List<String>, message: String): CommitOutcome? {
        if (paths.isEmpty()) return null

        val staged = git(root, "add", "--", *paths.toTypedArray())
        if (!staged.first) return CommitOutcome(root, false, staged.second)
        if (!hasStagedChanges(root)) return null

        val (ok, output) = git(root, "commit", "-m", message, "--", *paths.toTypedArray())

        return CommitOutcome(root, ok, output)
    }

    private fun commitOne(root: String, message: String): CommitOutcome? {
        val staged = git(root, "add", "-A")
        if (!staged.first) return CommitOutcome(root, false, staged.second)
        if (!hasStagedChanges(root)) return null

        val (ok, output) = git(root, "commit", "-m", message)

        return CommitOutcome(root, ok, output)
    }

    private fun updatePointers(superproject: String, submodules: List<String>, message: String): List<CommitOutcome> {
        for (submodule in submodules) {
            git(superproject, "add", submodule)
        }
        if (!hasStagedChanges(superproject)) return emptyList()

        val (ok, output) = git(superproject, "commit", "-m", "$message\n\nUpdate submodule pointers.")

        return listOf(CommitOutcome(superproject, ok, output))
    }

    private fun hasStagedChanges(root: String): Boolean =
        !git(root, "diff", "--cached", "--quiet").first

    internal fun isInside(candidate: String, parent: String?): Boolean {
        if (parent == null) return false
        val child = Path.of(candidate).normalize().toAbsolutePath()

        return child != Path.of(parent).normalize().toAbsolutePath() && child.startsWith(Path.of(parent))
    }

    private fun git(root: String, vararg args: String): Pair<Boolean, String> = try {
        val result = ProcessHelper.execWithTimeout(
            command = arrayOf("git", "-C", root, *args),
            timeoutMs = GIT_TIMEOUT_MS,
            extraEnv = mapOf("GIT_OPTIONAL_LOCKS" to "0")
        )
        (result.exitCode == 0) to result.output
    } catch (e: Exception) {
        false to (e.message ?: "exception")
    }
}
