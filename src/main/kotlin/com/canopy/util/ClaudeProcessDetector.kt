package com.canopy.util

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.nio.file.Files
import java.nio.file.Path

object ClaudeProcessDetector {

    private val SPARE_PROCESS = Regex("""\bclaude\b.*\b(daemon|bg-pty-host|bg-spare)\b""")
    private val WHITESPACE = Regex("\\s+")
    private const val PROCESS_LISTING_TTL_MS = 60_000L

    @Volatile private var listing: ProcessListing? = null
    private val sessionFiles = java.util.concurrent.ConcurrentHashMap<Path, SessionFile>()

    private val gson = Gson()

    fun detectExternalSessions(projectPath: String?, sessions: List<com.canopy.model.SessionDisplay> = emptyList()): Set<String> {
        if (projectPath == null) return emptySet()
        return try {
            val sessionsDir = Path.of(System.getProperty("user.home"), ".claude", "sessions")
            if (!Files.isDirectory(sessionsDir)) return emptySet()

            val paths = Files.list(sessionsDir).use { stream -> stream.filter { it.toString().endsWith(".json") }.toList() }
            sessionFiles.keys.retainAll(paths.toSet())
            // Claude Code's daemon writes session files for its own spare processes (kind="bg"); those are never a user's terminal.
            val candidates = paths.mapNotNull(::sessionFileAt)
                .filter { it.kind != "bg" && it.cwd == projectPath && isProcessAlive(it.pid) }
            if (candidates.isEmpty()) return emptySet()

            val excludedPids = listingKnowing(candidates.mapTo(HashSet()) { it.pid }).excluded
            candidates.filter { it.pid !in excludedPids }.flatMapTo(HashSet()) { file ->
                listOf(file.sessionId) + sessions.filter { file.name != null && (it.name == file.name || it.tabTitle == file.name) }
                    .map { it.sessionId }
            }
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun sessionFileAt(path: Path): SessionFile? {
        val modified = runCatching { Files.getLastModifiedTime(path).toMillis() }.getOrNull() ?: return null
        val size = runCatching { Files.size(path) }.getOrNull() ?: return null
        sessionFiles[path]?.takeIf { it.modified == modified && it.size == size }?.let { return it }

        return runCatching {
            val obj = gson.fromJson(Files.readString(path), JsonObject::class.java)
            SessionFile(
                modified = modified,
                size = size,
                pid = obj.get("pid")?.asInt ?: return null,
                sessionId = obj.get("sessionId")?.asString ?: return null,
                cwd = obj.get("cwd")?.asString ?: return null,
                kind = obj.get("kind")?.asString,
                name = obj.get("name")?.asString
            )
        }.getOrNull()?.also { sessionFiles[path] = it }
    }

    private fun listingKnowing(pids: Set<Int>): ProcessListing {
        val now = System.currentTimeMillis()
        listing?.takeIf { now - it.takenAt < PROCESS_LISTING_TTL_MS && it.listed.containsAll(pids) }?.let { return it }

        return readListing(now).also { listing = it }
    }

    private fun readListing(now: Long): ProcessListing {
        val lines = try {
            ProcessHelper.execWithTimeout(
                command = arrayOf("ps", "-A", "-o", "pid,args", "-ww"),
                timeoutMs = 10_000
            ).output.lines().map { it.trim() }
        } catch (_: Exception) {
            emptyList()
        }
        val pidOf = { line: String -> line.split(WHITESPACE, limit = 2).firstOrNull()?.toIntOrNull() }

        return ProcessListing(
            listed = lines.mapNotNullTo(HashSet(), pidOf),
            excluded = lines.filter { it.contains("canopy-settings") || SPARE_PROCESS.containsMatchIn(it) }.mapNotNullTo(HashSet(), pidOf),
            takenAt = now
        )
    }

    private fun isProcessAlive(pid: Int): Boolean {
        return try {
            ProcessHandle.of(pid.toLong()).map { it.isAlive }.orElse(false)
        } catch (_: Exception) {
            false
        }
    }

    private class ProcessListing(val listed: Set<Int>, val excluded: Set<Int>, val takenAt: Long)

    private class SessionFile(
        val modified: Long,
        val size: Long,
        val pid: Int,
        val sessionId: String,
        val cwd: String,
        val kind: String?,
        val name: String?
    )
}
