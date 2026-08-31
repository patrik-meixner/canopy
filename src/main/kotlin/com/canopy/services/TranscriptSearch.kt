package com.canopy.services

import com.canopy.toolwindow.humanMessageIn
import com.canopy.util.ClaudePathEncoder
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

data class TranscriptHit(
    val sessionId: String,
    val ordinal: Int,
    val text: String,
    val atMillis: Long
)

/**
 * Full-text search over what you asked every session.
 *
 * There is no index, and deliberately so: a raw substring test on the undecoded line rejects
 * upwards of ninety-nine percent of a transcript, and JSON is parsed only for the lines that
 * survive it. Building and invalidating an index over a gigabyte of history would cost more than
 * the scan it replaces.
 */
object TranscriptSearch {

    private const val PER_SESSION_LIMIT = 20
    private val gson = Gson()

    fun search(
        projectBasePath: String,
        query: String,
        withinDays: Int,
        cancelled: AtomicBoolean
    ): List<TranscriptHit> {
        val needle = query.trim()
        if (needle.length < 2) return emptyList()
        val cutoff = Instant.now().minus(Duration.ofDays(withinDays.toLong()))

        return transcripts(projectBasePath)
            .filter { modifiedAfter(it, cutoff) }
            .sortedByDescending { runCatching { Files.getLastModifiedTime(it).toMillis() }.getOrDefault(0L) }
            .takeWhile { !cancelled.get() }
            .flatMap { hitsIn(it, needle, cancelled) }
    }

    private fun transcripts(basePath: String): List<Path> =
        ClaudePathEncoder.projectDirCandidates(basePath)
            .filter { Files.isDirectory(it) }
            .flatMap { directory ->
                runCatching { Files.list(directory).use { it.toList() } }.getOrDefault(emptyList())
            }
            .filter { it.toString().endsWith(".jsonl") }

    private fun modifiedAfter(path: Path, cutoff: Instant): Boolean =
        runCatching { Files.getLastModifiedTime(path).toInstant().isAfter(cutoff) }.getOrDefault(false)

    private fun hitsIn(path: Path, needle: String, cancelled: AtomicBoolean): List<TranscriptHit> {
        val sessionId = path.fileName.toString().removeSuffix(".jsonl")
        val hits = mutableListOf<TranscriptHit>()
        var ordinal = 0

        try {
            Files.newBufferedReader(path).use { reader ->
                while (true) {
                    if (cancelled.get() || hits.size >= PER_SESSION_LIMIT) break
                    val line = reader.readLine() ?: break
                    if (!line.contains(needle, ignoreCase = true)) continue

                    val record = runCatching { gson.fromJson(line, JsonObject::class.java) }.getOrNull() ?: continue
                    val message = humanMessageIn(record, ++ordinal) ?: continue
                    if (!message.text.contains(needle, ignoreCase = true)) continue

                    hits.add(TranscriptHit(sessionId, ordinal, message.text, modifiedMillis(path)))
                }
            }
        } catch (_: Exception) {
            return hits
        }

        return hits
    }

    private fun modifiedMillis(path: Path): Long =
        runCatching { Files.getLastModifiedTime(path).toMillis() }.getOrDefault(0L)
}

private const val SNIPPET_RADIUS = 60

/** The match in the middle of its own sentence, which is what makes a result list scannable. */
fun snippetAround(text: String, query: String): String {
    val single = text.replace(Regex("\\s+"), " ").trim()
    val at = single.indexOf(query, ignoreCase = true)
    if (at < 0) return single.take(SNIPPET_RADIUS * 2)

    val from = (at - SNIPPET_RADIUS).coerceAtLeast(0)
    val to = (at + query.length + SNIPPET_RADIUS).coerceAtMost(single.length)

    return (if (from > 0) "…" else "") + single.substring(from, to) + (if (to < single.length) "…" else "")
}
