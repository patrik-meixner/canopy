package com.canopy.insight

private val CD_PREFIX = Regex("""^cd\s+(?:'[^']*'|"[^"]*"|\S+)\s*&&\s*""")

private val ENV_PREFIX = Regex("""^(?:[A-Z_][A-Z0-9_]*=(?:'[^']*'|"[^"]*"|\S*)\s+)+""")

private val NOISE_SUFFIX = Regex("""\s*(?:2>&1|>/dev/null|>\s*/dev/null)\s*""")

/**
 * What a shell call was actually for.
 *
 * Every command an agent runs starts by putting itself in the right directory, so a log of raw
 * commands is a column of `cd /long/path && ` with the interesting part off the right edge.
 */
data class CommandSummary(val verb: String, val rest: String)

fun summarizeCommand(command: String): CommandSummary {
    val stripped = command.trim()
        .replace(CD_PREFIX, "")
        .replace(ENV_PREFIX, "")
        .replace(NOISE_SUFFIX, " ")
        .trim()
    if (stripped.isEmpty()) return CommandSummary("cd", "")

    val verb = stripped.substringBefore(' ').substringAfterLast('/')
    val rest = stripped.removePrefix(stripped.substringBefore(' ')).trim()

    return CommandSummary(verb, rest)
}
