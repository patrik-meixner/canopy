package com.canopy.toolwindow

import com.google.gson.JsonObject

data class SessionMessage(
    val ordinal: Int,
    val text: String,
    val images: List<String>
) {
    /** First non-empty line, which is what the terminal search and the row title both key on. */
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

/**
 * A message the human actually typed, or null for everything else on the `user` channel.
 *
 * Tool results, image markers the CLI writes back, interrupt markers and resume preambles all
 * arrive as user records. Listing them turned the panel into a log of the plugin's own plumbing.
 */
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
