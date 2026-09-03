package com.canopy.session

import com.google.gson.JsonObject

private const val WHOLE_FILE_TOOL = "Write"
private const val REPLACEMENT_TOOL = "Edit"

fun fileWritesIn(record: JsonObject): List<FileWrite> {
    if (record.get("type")?.asString != "assistant") return emptyList()
    val content = record.getAsJsonObject("message")?.getAsJsonArray("content") ?: return emptyList()

    return content
        .filter { it.isJsonObject }
        .map { it.asJsonObject }
        .filter { it.get("type")?.asString == "tool_use" }
        .mapNotNull(::writeIn)
}

private fun writeIn(toolUse: JsonObject): FileWrite? {
    val input = toolUse.getAsJsonObject("input") ?: return null
    val path = input.text("file_path") ?: return null

    return when (toolUse.get("name")?.asString) {
        WHOLE_FILE_TOOL -> input.text("content")?.let { FileWrite.Whole(path, it) }
        REPLACEMENT_TOOL -> {
            val before = input.text("old_string") ?: return null
            val after = input.text("new_string") ?: return null
            FileWrite.Replace(path, before, after)
        }
        else -> null
    }
}

private fun JsonObject.text(field: String): String? =
    get(field)?.takeIf { it.isJsonPrimitive }?.asString
