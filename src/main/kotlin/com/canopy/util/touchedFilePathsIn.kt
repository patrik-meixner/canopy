package com.canopy.util

import com.google.gson.JsonObject

private val MUTATING_TOOLS = setOf("Edit", "Write", "MultiEdit", "NotebookEdit")

private val PATH_KEYS = listOf("file_path", "notebook_path", "path")

private val ABSOLUTE_PATH = Regex("""(/(?:Users|home|opt|srv|var|tmp|private)/[A-Za-z0-9._@%+-]+(?:/[A-Za-z0-9._@%+-]+)+)""")

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

private val WRITING_VERB = Regex(
    """(^|[;&|(`]\s*|\s)(sudo\s+)?(sed\s+-\S*i|perl\s+-\S*i|awk\s+-i|tee|mv|cp|rm|rmdir|mkdir|touch|ln|install|patch|dd|chmod|chown|rsync|unzip|tar|""" +
        """open\([^)]*['"][wax]|""" +
        """git(\s+-C\s+\S+)?\s+(add|commit|apply|am|checkout|restore|rm|mv|reset|revert|merge|rebase|cherry-pick|clean|init|clone|""" +
        """stash(?!\s+(list|show))|worktree\s+(add|remove|move|prune|repair|lock|unlock)))\b"""
)

private val REDIRECTION = Regex("""(?<![0-9&\-])>""")

private val CLAUSE_BREAK = Regex("""\r?\n|;|&&|\|\||\|""")

private val CHANGE_DIRECTORY = Regex("""(^|[;&|(`]\s*|\s)cd\s+["']?(/(?:Users|home|opt|srv|var|tmp|private)/[A-Za-z0-9._@%+-]+(?:/[A-Za-z0-9._@%+-]+)*)""")

internal fun pathsInCommand(command: String?): List<String> {
    if (command.isNullOrBlank()) return emptyList()

    val touched = LinkedHashSet<String>()
    var workingDirectory: String? = null
    for (clause in command.split(CLAUSE_BREAK)) {
        changeDirectoryIn(clause)?.let { workingDirectory = it }
        val written = pathsWrittenBy(clause)
        touched += written
        if (written.isEmpty() && isWriting(clause)) workingDirectory?.let(touched::add)
    }

    return touched.toList()
}

private fun changeDirectoryIn(clause: String): String? = CHANGE_DIRECTORY.find(clause)?.groupValues?.get(2)

private fun isWriting(clause: String): Boolean =
    WRITING_VERB.containsMatchIn(clause) || REDIRECTION.containsMatchIn(clause)

private fun pathsWrittenBy(clause: String): List<String> {
    val redirection = REDIRECTION.find(clause)
    val scope = when {
        redirection != null -> clause.substring(redirection.range.last + 1)
        WRITING_VERB.containsMatchIn(clause) -> clause
        else -> return emptyList()
    }

    return ABSOLUTE_PATH.findAll(scope).map { it.value }.toList()
}
