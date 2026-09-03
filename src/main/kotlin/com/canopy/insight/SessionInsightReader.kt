package com.canopy.insight

import com.canopy.toolwindow.SessionMessage
import com.canopy.toolwindow.humanMessageIn
import com.canopy.util.JsonlTailReader
import com.canopy.util.touchedFilePathsIn
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.nio.file.Path

class SessionInsightReader {

    private val gson = Gson()
    private val tail = JsonlTailReader()
    private val messages = mutableListOf<SessionMessage>()
    private val activity = mutableListOf<ActivityEntry>()
    private val writes = LinkedHashMap<String, Int>()
    private val repositories = HashMap<String, String>()
    private var revision = 0L
    private var snapshot: SessionInsight? = null

    fun reset() {
        tail.reset()
        clear()
    }

    @Synchronized
    fun read(path: Path): SessionInsight {
        val before = revision
        tail.consume(path, onRestart = ::clear, onLine = ::consumeLine)
        if (revision == before && snapshot != null) return snapshot!!

        val fresh = SessionInsight(
            revision = revision,
            messages = messages.toList(),
            activity = activity.takeLast(ACTIVITY_LIMIT),
            files = writes.entries
                .map { TouchedFile(it.key, it.value, repositoryOf(it.key)) }
                .sortedWith(compareBy({ it.repository }, { -it.writes }))
        )
        snapshot = fresh

        return fresh
    }

    private fun repositoryOf(path: String): String {
        val directory = Path.of(path).parent ?: return OUTSIDE_ANY_REPOSITORY
        val root = com.canopy.util.GitRootCache.rootOf(directory) ?: return OUTSIDE_ANY_REPOSITORY

        return repositories.getOrPut(root) { Path.of(root).fileName?.toString() ?: root }
    }

    private fun clear() {
        revision++
        snapshot = null
        messages.clear()
        activity.clear()
        writes.clear()
    }

    private fun consumeLine(line: String) {
        if (line.isBlank()) return
        val record = runCatching { gson.fromJson(line, JsonObject::class.java) }.getOrNull() ?: return

        humanMessageIn(record, messages.size + 1)?.let { messages.add(it); revision++ }
        touchedFilePathsIn(record).forEach { writes[it] = (writes[it] ?: 0) + 1; revision++ }

        val content = record.getAsJsonObject("message")?.get("content") ?: return
        if (!content.isJsonArray) return
        val at = timestampOf(record)

        for (element in content.asJsonArray) {
            if (!element.isJsonObject) continue
            val block = element.asJsonObject
            when (block.get("type")?.asString) {
                "tool_use" -> onToolUse(block, at)
            }
        }
    }

    private fun onToolUse(block: JsonObject, at: Long) {
        val name = block.get("name")?.asString ?: return
        val input = block.getAsJsonObject("input")

        activity.add(ActivityEntry(name, activityDetail(name, input), at, messages.size))
        revision++
        if (activity.size > ACTIVITY_LIMIT * 2) activity.subList(0, activity.size - ACTIVITY_LIMIT).clear()
    }

    private fun timestampOf(record: JsonObject): Long =
        record.get("timestamp")?.takeIf { it.isJsonPrimitive }?.asString
            ?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
            ?: 0L

    private companion object {
        const val ACTIVITY_LIMIT = 300
    }
}

private const val DETAIL_LENGTH = 120

internal fun activityDetail(tool: String, input: JsonObject?): String {
    if (input == null) return ""
    val keys = when (tool) {
        "Bash" -> listOf("command", "description")
        "Task", "Agent" -> listOf("description", "prompt")
        "TaskCreate" -> listOf("subject")
        "TaskUpdate" -> listOf("taskId")
        else -> listOf("file_path", "notebook_path", "path", "pattern", "query", "url", "skill")
    }
    val raw = keys.firstNotNullOfOrNull { input.get(it)?.takeIf { value -> value.isJsonPrimitive }?.asString }.orEmpty()
    val single = raw.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()

    return if (single.length > DETAIL_LENGTH) single.take(DETAIL_LENGTH - 1) + "…" else single
}

private const val OUTSIDE_ANY_REPOSITORY = "Outside any repository"
