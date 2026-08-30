package com.canopy.util

import com.google.gson.JsonObject

private val MUTATING_TOOLS = setOf("Edit", "Write", "MultiEdit", "NotebookEdit")

private val PATH_KEYS = listOf("file_path", "notebook_path", "path")

/**
 * Absolute paths an assistant message wrote to.
 *
 * Reads are deliberately excluded: an agent opens hundreds of files to change three, so a list
 * that included them would say nothing about where the work landed.
 */
fun touchedFilePathsIn(message: JsonObject): List<String> {
    val content = message.getAsJsonObject("message")?.get("content") ?: return emptyList()
    if (!content.isJsonArray) return emptyList()

    return content.asJsonArray
        .filter { it.isJsonObject }
        .map { it.asJsonObject }
        .filter { it.get("type")?.asString == "tool_use" }
        .filter { it.get("name")?.asString in MUTATING_TOOLS }
        .mapNotNull { pathFromInput(it.getAsJsonObject("input")) }
}

private fun pathFromInput(input: JsonObject?): String? {
    if (input == null) return null

    return PATH_KEYS
        .firstNotNullOfOrNull { input.get(it)?.takeIf { value -> value.isJsonPrimitive } }
        ?.asString
        ?.takeIf { it.isNotBlank() }
}
