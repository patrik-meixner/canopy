package com.canopy.model

/**
 * Whose turn the end of a transcript says it is.
 *
 * A raw role was not enough to answer that: the CLI writes its own bookkeeping as user records, so
 * a session left sitting on a compact summary or a slash-command echo claimed the agent was still
 * mid-turn and spun forever.
 */
enum class TranscriptTail { AssistantReply, AgentsTurn, Housekeeping, Interrupted }

private val CLI_OWN_RECORD = listOf("<command-name>", "<local-command-stdout>", "<local-command-caveat>")

private const val INTERRUPT_MARKER = "[Request interrupted by user"

fun transcriptTailOf(
    type: String?,
    isCompactSummary: Boolean,
    isMeta: Boolean,
    hasToolUseResult: Boolean,
    text: String
): TranscriptTail? {
    if (type == "assistant") return TranscriptTail.AssistantReply
    if (type != "user") return null
    if (isCompactSummary || isMeta) return TranscriptTail.Housekeeping
    if (text.startsWith(INTERRUPT_MARKER)) return TranscriptTail.Interrupted
    if (hasToolUseResult) return TranscriptTail.AgentsTurn
    if (CLI_OWN_RECORD.any { it in text }) return TranscriptTail.Housekeeping

    return TranscriptTail.AgentsTurn
}
