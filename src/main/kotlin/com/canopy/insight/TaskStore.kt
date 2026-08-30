package com.canopy.insight

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.nio.file.Files
import java.nio.file.Path

/**
 * The session's task list, read from where Claude Code keeps it: one JSON file per task under
 * `~/.claude/tasks/<session id>/`.
 *
 * Folding TaskCreate and TaskUpdate calls out of the transcript would rebuild the same list from
 * its edit history, and get it wrong the moment a task is changed by anything but a tool call.
 */
object TaskStore {

    private val gson = Gson()

    fun directoryFor(sessionId: String): Path =
        Path.of(System.getProperty("user.home"), ".claude", "tasks", sessionId)

    fun read(sessionId: String): List<PlannedTask> {
        val directory = directoryFor(sessionId)
        if (!Files.isDirectory(directory)) return emptyList()

        return try {
            Files.list(directory).use { stream ->
                stream.toList()
                    .filter { it.fileName.toString().endsWith(".json") }
                    .mapNotNull { taskAt(it) }
                    .sortedBy { it.id.toIntOrNull() ?: Int.MAX_VALUE }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Cheap enough to run on a tick: one stat per task file, no parsing. */
    fun signature(sessionId: String): String {
        val directory = directoryFor(sessionId)
        if (!Files.isDirectory(directory)) return ""

        return try {
            Files.list(directory).use { stream ->
                stream.toList()
                    .filter { it.fileName.toString().endsWith(".json") }
                    .sortedBy { it.fileName.toString() }
                    .joinToString("|") { "${it.fileName}:${Files.getLastModifiedTime(it).toMillis()}" }
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun taskAt(file: Path): PlannedTask? = try {
        plannedTaskOf(gson.fromJson(Files.readString(file), JsonObject::class.java))
    } catch (_: Exception) {
        null
    }
}

internal fun plannedTaskOf(json: JsonObject?): PlannedTask? {
    val id = json?.get("id")?.takeIf { it.isJsonPrimitive }?.asString ?: return null
    val subject = json.get("subject")?.takeIf { it.isJsonPrimitive }?.asString ?: return null

    return PlannedTask(
        id = id,
        subject = subject,
        description = json.get("description")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
        activeForm = json.get("activeForm")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
        status = statusOf(json.get("status")?.takeIf { it.isJsonPrimitive }?.asString),
        blockedBy = json.getAsJsonArray("blockedBy")?.mapNotNull { it.asString } ?: emptyList()
    )
}

internal fun statusOf(raw: String?): TaskStatus = when (raw) {
    "in_progress" -> TaskStatus.IN_PROGRESS
    "completed" -> TaskStatus.COMPLETED
    else -> TaskStatus.PENDING
}
