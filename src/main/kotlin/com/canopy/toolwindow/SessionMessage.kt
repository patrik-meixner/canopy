package com.canopy.toolwindow

import com.google.gson.JsonObject

data class SessionMessage(
    val ordinal: Int,
    val text: String,
    val images: List<String>
) {
    val headline: String
        get() = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
}

private val SYNTHETIC_PREFIXES = listOf(
    "<task-notification>",
    "<local-command",
    "<command-name>",
    "[Request interrupted by user]",
    "This session is being continued from a previous",
    "Caveat: The messages below were generated",
    "Base directory for this skill:"
)

fun humanMessageIn(record: JsonObject, ordinal: Int): SessionMessage? {
    if (record.get("type")?.asString != "user") return null
    if (record.get("isMeta")?.asBoolean == true) return null
    if (record.has("toolUseResult")) return null

    val content = record.getAsJsonObject("message")?.get("content") ?: return null
    val text: String
    val images = mutableListOf<String>()

    if (content.isJsonPrimitive) {
        text = content.asString
    } else if (content.isJsonArray) {
        val blocks = content.asJsonArray.filter { it.isJsonObject }.map { it.asJsonObject }
        text = blocks.firstOrNull { it.get("type")?.asString == "text" }?.get("text")?.asString.orEmpty()
        blocks.filter { it.get("type")?.asString == "image" }
            .mapNotNullTo(images) { it.getAsJsonObject("source")?.get("data")?.asString }
    } else {
        return null
    }

    val trimmed = text.trim()
    if (trimmed.isEmpty() && images.isEmpty()) return null
    if (SYNTHETIC_PREFIXES.any { trimmed.startsWith(it) }) return null

    return SessionMessage(ordinal, trimmed, images)
}

fun withoutRepeats(messages: List<SessionMessage>): List<SessionMessage> =
    messages.filterIndexed { index, message ->
        index == 0 || messages[index - 1].text != message.text || message.images.isNotEmpty()
    }

fun agentTextIn(record: JsonObject): String? {
    if (record.get("type")?.asString != "assistant") return null

    val content = record.getAsJsonObject("message")?.get("content") ?: return null
    if (!content.isJsonArray) return null

    val spoken = content.asJsonArray
        .filter { it.isJsonObject }
        .map { it.asJsonObject }
        .filter { it.get("type")?.asString == "text" }
        .mapNotNull { it.get("text")?.asString }
        .joinToString("\n")
        .trim()

    return spoken.ifEmpty { null }
}
