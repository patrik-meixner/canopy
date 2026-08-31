package com.canopy.toolwindow

import com.canopy.util.ProcessHelper
import java.nio.file.Files
import java.nio.file.Path

data class RevertOutcome(
    val root: String,
    val restored: Int,
    val deleted: Int,
    val undoRef: String?,
    val failure: String?,
)

/**
 * Puts files back the way HEAD has them, and removes the ones git never knew about.
 *
 * A checkpoint is taken first, so the thing being undone is itself undoable: the reverted state is
 * a stash object under the plugin's own ref namespace, and nothing about it depends on the stash
 * stack a person might be using for something else.
 */
object SessionRevert {

    private const val GIT_TIMEOUT_MS = 60_000L

    fun revert(root: String, tracked: List<String>, untracked: List<String>, sessionId: String): RevertOutcome {
        if (tracked.isEmpty() && untracked.isEmpty()) {
            return RevertOutcome(root, 0, 0, null, null)
        }

        val undo = Checkpoints.create(root, sessionId, "before revert")

        val restored = tracked.map { relativeTo(root, it) }
        val failure = restored
            .chunked(CHUNK)
            .firstNotNullOfOrNull { batch ->
                val result = git(root, "checkout", "HEAD", "--", *batch.toTypedArray())
                if (result.first) null else result.second.take(400)
            }
        if (failure != null) return RevertOutcome(root, 0, 0, undo?.commit, failure)

        val deleted = untracked.count { path -> runCatching { Files.deleteIfExists(Path.of(path)) }.getOrDefault(false) }

        return RevertOutcome(root, restored.size, deleted, undo?.commit, null)
    }

    /** Long selections can exceed the command-line limit, and a half-applied revert is the worst outcome. */
    private const val CHUNK = 200

    private fun relativeTo(root: String, path: String): String =
        runCatching { Path.of(root).relativize(Path.of(path)).toString() }.getOrDefault(path)

    private fun git(root: String, vararg args: String): Pair<Boolean, String> = try {
        val result = ProcessHelper.execWithTimeout(
            command = arrayOf("git", "-C", root, *args),
            timeoutMs = GIT_TIMEOUT_MS,
            extraEnv = mapOf("GIT_OPTIONAL_LOCKS" to "0"),
        )
        (result.exitCode == 0) to result.output
    } catch (e: Exception) {
        false to (e.message ?: "exception")
    }
}
