package com.canopy.util

import com.google.gson.JsonObject

private val MUTATING_TOOLS = setOf("Edit", "Write", "MultiEdit", "NotebookEdit")

private val PATH_KEYS = listOf("file_path", "notebook_path", "path")

private val ABSOLUTE_PATH = Regex("""(/(?:Users|home|opt|srv|var|tmp|private)/[A-Za-z0-9._@%+-]+(?:/[A-Za-z0-9._@%+-]+)+)""")

/**
 * Absolute paths an assistant message worked on.
 *
 * Reads are excluded: an agent opens hundreds of files to change three. Shell commands are not,
 * because an agent edits through the shell far more often than through the edit tools — a session
 * that ran nothing but heredocs and sed would otherwise look like it touched nothing at all, and
 * the repository it actually worked in would never be attributed to it.
 */
fun touchedFilePathsIn(message: JsonObject): List<String> {
    val content = message.getAsJsonObject("message")?.get("content") ?: return emptyList()
    if (!content.isJsonArray) return emptyList()

    return content.asJsonArray
        .filter { it.isJsonObject }
        .map { it.asJsonObject }
        .filter { it.get("type")?.asString == "tool_use" }
        .flatMap { pathsFromToolUse(it) }
}

private fun pathsFromToolUse(toolUse: JsonObject): List<String> {
    val name = toolUse.get("name")?.asString
    val input = toolUse.getAsJsonObject("input")

    if (name in MUTATING_TOOLS) return listOfNotNull(pathFromInput(input))
    if (name != "Bash") return emptyList()

    return pathsInCommand(input?.get("command")?.takeIf { it.isJsonPrimitive }?.asString)
}

private fun pathFromInput(input: JsonObject?): String? {
    if (input == null) return null

    return PATH_KEYS
        .firstNotNullOfOrNull { input.get(it)?.takeIf { value -> value.isJsonPrimitive } }
        ?.asString
        ?.takeIf { it.isNotBlank() }
}

/** Absolute paths named anywhere in a shell command; a directory that is not a repository is dropped later. */
internal fun pathsInCommand(command: String?): List<String> {
    if (command.isNullOrBlank()) return emptyList()

    return ABSOLUTE_PATH.findAll(command).map { it.value }.distinct().toList()
}
