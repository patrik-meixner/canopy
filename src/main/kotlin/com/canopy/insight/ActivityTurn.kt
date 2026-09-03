package com.canopy.insight

data class ActivityTurn(
    val ordinal: Int,
    val prompt: String,
    val atMillis: Long,
    val filesWritten: List<String>,
    val commandsRun: Int,
    val commandVerbs: List<String>,
    val lookups: Int
)

private val LOOKUP_TOOLS = setOf("Read", "Glob", "Grep", "WebFetch", "WebSearch")

fun activityTurns(
    entries: List<ActivityEntry>,
    promptsByTurn: Map<Int, String>,
    writesOnly: Boolean
): List<ActivityTurn> {
    val turns = entries.groupBy { it.turn }.map { (turn, ofTurn) ->
        val written = ofTurn.filter { isWrite(it.tool) }.map { it.detail }.filter { it.isNotBlank() }.distinct()

        ActivityTurn(
            ordinal = turn,
            prompt = promptsByTurn[turn].orEmpty(),
            atMillis = ofTurn.first().atMillis,
            filesWritten = written,
            commandsRun = ofTurn.count { it.tool == "Bash" },
            commandVerbs = ofTurn.filter { it.tool == "Bash" }
                .map { summarizeCommand(it.detail).verb }
                .filter { it.isNotBlank() }
                .distinct()
                .take(NAMED_VERBS),
            lookups = ofTurn.count { it.tool in LOOKUP_TOOLS }
        )
    }
    if (!writesOnly) return turns

    return turns.filter { it.filesWritten.isNotEmpty() }
}

fun turnSummary(turn: ActivityTurn): String {
    val written = turn.filesWritten.size.takeIf { it > 0 }?.let { "$it file${plural(it)} written" }
    val named = turn.commandVerbs.joinToString(", ").takeIf { it.isNotEmpty() }?.let { " ($it)" }.orEmpty()
    val ran = turn.commandsRun.takeIf { it > 0 }?.let { "$it command${plural(it)}$named" }
    val read = turn.lookups.takeIf { it > 0 }?.let { "$it lookup${plural(it)}" }

    return listOfNotNull(written, ran, read).joinToString("   ").ifEmpty { "Nothing recorded" }
}

private fun plural(count: Int) = if (count == 1) "" else "s"

private const val NAMED_VERBS = 4
