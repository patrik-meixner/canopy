package com.canopy.toolwindow

import com.canopy.util.ProcessHelper
import java.time.Instant

data class Checkpoint(
    val commit: String,
    val atMillis: Long,
    val label: String
)

/**
 * The working tree as it stood at the end of a turn, kept without touching it.
 *
 * `git stash create` writes a commit object and changes nothing on disk; a ref under
 * `refs/canopy` keeps it from being collected. When an agent goes wrong the alternative is
 * `git checkout .`, which throws away the good half of its work along with the bad.
 */
object Checkpoints {

    private const val GIT_TIMEOUT_MS = 30_000L
    private const val NAMESPACE = "refs/canopy"

    fun create(root: String, sessionId: String, label: String): Checkpoint? {
        val commit = git(root, "stash", "create", label)?.trim()?.ifEmpty { null } ?: return null
        val at = System.currentTimeMillis()

        git(root, "update-ref", refFor(sessionId, at, label), commit) ?: return null

        return Checkpoint(commit, at, label)
    }

    fun list(root: String, sessionId: String): List<Checkpoint> {
        val output = git(root, "for-each-ref", "--format=%(refname)|%(objectname)", "$NAMESPACE/$sessionId")
            ?: return emptyList()

        return parseCheckpoints(output).sortedByDescending { it.atMillis }
    }

    /** Restores the files that existed then; anything created since is left where it is. */
    fun restore(root: String, commit: String): Pair<Boolean, String> {
        val result = ProcessHelper.execWithTimeout(arrayOf("git", "-C", root, "checkout", commit, "--", "."), GIT_TIMEOUT_MS)

        return (result.exitCode == 0) to result.output
    }

    fun refFor(sessionId: String, atMillis: Long, label: String): String =
        "$NAMESPACE/$sessionId/$atMillis-${slug(label)}"

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

/** A ref name cannot hold spaces or most punctuation, and the label is free text. */
internal fun slug(label: String): String =
    label.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40).ifEmpty { "checkpoint" }

internal fun parseCheckpoints(output: String): List<Checkpoint> =
    output.lineSequence()
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val (ref, commit) = line.split('|').let { it.getOrNull(0) to it.getOrNull(1) }
            if (ref == null || commit.isNullOrBlank()) return@mapNotNull null
            val tail = ref.substringAfterLast('/')
            val at = tail.substringBefore('-').toLongOrNull() ?: return@mapNotNull null

            Checkpoint(commit, at, tail.substringAfter('-').replace('-', ' '))
        }
        .toList()

internal fun checkpointLabel(at: Long): String =
    java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(java.time.ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(at))
