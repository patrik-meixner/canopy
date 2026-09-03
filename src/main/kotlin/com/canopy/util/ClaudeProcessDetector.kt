package com.canopy.util

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.nio.file.Files
import java.nio.file.Path

object ClaudeProcessDetector {

    private val SPARE_PROCESS = Regex("""\bclaude\b.*\b(daemon|bg-pty-host|bg-spare)\b""")
    private val WHITESPACE = Regex("\\s+")
    private const val EXCLUDED_PIDS_TTL_MS = 10_000L

    @Volatile private var excludedPids: Set<Int>? = null
    @Volatile private var excludedPidsAt = 0L

    private val gson = Gson()
    fun detectExternalSessions(projectPath: String?, sessions: List<com.canopy.model.SessionDisplay> = emptyList()): Set<String> {
        if (projectPath == null) return emptySet()
        return try {
            val excludedPids = getSelfExcludedPids()
            val sessionsDir = Path.of(System.getProperty("user.home"), ".claude", "sessions")
            if (!Files.isDirectory(sessionsDir)) return emptySet()

            val result = mutableSetOf<String>()
            Files.list(sessionsDir).use { stream ->
                stream.filter { it.toString().endsWith(".json") }
                    .forEach { path ->
                        try {
                            val obj = gson.fromJson(Files.readString(path), JsonObject::class.java)
                            val pid = obj.get("pid")?.asInt ?: return@forEach
                            val sessionId = obj.get("sessionId")?.asString ?: return@forEach
                            val cwd = obj.get("cwd")?.asString ?: return@forEach

                            // Claude Code's daemon/background architecture writes session
                            // files for its own spare processes (kind="bg"). Those are never
                            // a user's external terminal, so don't count them.
                            if (obj.get("kind")?.asString == "bg") return@forEach

                            if (cwd != projectPath || pid in excludedPids || !isProcessAlive(pid)) return@forEach

                            result.add(sessionId)

                            val name = obj.get("name")?.asString
                            if (name != null) {
                                sessions.filter { it.name == name || it.tabTitle == name }
                                    .forEach { result.add(it.sessionId) }
                            }
                        } catch (_: Exception) {}
                    }
            }
            result
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun getSelfExcludedPids(): Set<Int> {
        val now = System.currentTimeMillis()
        excludedPids?.takeIf { now - excludedPidsAt < EXCLUDED_PIDS_TTL_MS }?.let { return it }

        return readExcludedPids().also {
            excludedPids = it
            excludedPidsAt = now
        }
    }

    private fun readExcludedPids(): Set<Int> {
        return try {
            val res = ProcessHelper.execWithTimeout(
                command = arrayOf("ps", "-A", "-o", "pid,args", "-ww"),
                timeoutMs = 10_000
            )
            res.output.lines()
                .map { it.trim() }
                .filter { line -> line.contains("canopy-settings") || SPARE_PROCESS.containsMatchIn(line) }
                .mapNotNull { it.split(WHITESPACE, limit = 2).firstOrNull()?.toIntOrNull() }
                .toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun isProcessAlive(pid: Int): Boolean {
        return try {
            ProcessHandle.of(pid.toLong()).map { it.isAlive }.orElse(false)
        } catch (_: Exception) {
            false
        }
    }
}
