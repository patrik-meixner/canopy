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

private val WRITING_VERB = Regex(
    """(^|[;&|(`]\s*|\s)(sudo\s+)?(sed\s+-\S*i|perl\s+-\S*i|awk\s+-i|tee|mv|cp|rm|rmdir|mkdir|touch|ln|install|patch|dd|chmod|chown|rsync|unzip|tar|""" +
        """git\s+(add|commit|apply|am|checkout|restore|rm|mv|stash|reset|revert|merge|rebase|cherry-pick|clean|init|clone|worktree))\b"""
)

/** A redirection, but not `2>&1`, `1>&2` or a `->` inside a string. */
private val REDIRECTION = Regex("""(?<![0-9&\-])>""")

private val CLAUSE_BREAK = Regex("""\r?\n|;|&&|\|\||\|""")

private val CHANGE_DIRECTORY = Regex("""(^|[;&|(`]\s*|\s)cd\s+["']?(/(?:Users|home|opt|srv|var|tmp|private)/[A-Za-z0-9._@%+-]+(?:/[A-Za-z0-9._@%+-]+)*)""")

/**
 * Absolute paths a shell command wrote to, and nothing it merely looked at.
 *
 * An agent edits through the shell far more often than through the edit tools, so these paths are
 * what attributes a repository to a session at all. Taking every path a command names attributed
 * whatever the agent grepped, found or listed - a session that ran one `find` across the disk then
 * claimed every repository it walked past.
 *
 * A `cd` to an absolute directory stands for the relative writes that follow it, which is every
 * heredoc and `sed -i file` an agent runs after changing directory.
 */
internal fun pathsInCommand(command: String?): List<String> {
    if (command.isNullOrBlank()) return emptyList()

    val clauses = command.split(CLAUSE_BREAK)
    val written = clauses.flatMap(::pathsWrittenBy)
    if (written.isEmpty() && clauses.none(::isWriting)) return emptyList()

    val workedIn = CHANGE_DIRECTORY.findAll(command).map { it.groupValues[2] }
    return (workedIn + written).distinct().toList()
}

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
