package com.canopy.model

/**
 * How much a session wants you, derived from the notify state the CLI writes.
 *
 * [rank] orders the list: with several agents running, the one blocked on a permission prompt
 * has to surface above one that is merely thinking, or you find it by clicking through tabs.
 */
enum class SessionAttention(val rank: Int, val glyph: String) {
    NeedsPermission(0, "!"),
    /** A finished turn is your turn, not an alarm: it reads as a prompt, not a question. */
    WaitingForInput(1, "›"),
    Working(2, "*"),
    Compacting(2, "~"),
    None(3, "");

    val isBlocking: Boolean get() = this == NeedsPermission || this == WaitingForInput
}

/**
 * What a session wants from you.
 *
 * The CLI's notify state is exact but only exists for sessions this plugin launched. For one
 * running in an outside terminal the transcript is the only evidence, and it is read solely when
 * the session is actually running — otherwise every finished session from months ago would look
 * like it were waiting.
 */
fun sessionAttentionFor(
    notifyState: String?,
    isRunning: Boolean,
    tail: TranscriptTail?,
    idleForMillis: Long = 0
): SessionAttention {
    val reported = notifyState?.let(::sessionAttentionOf)?.takeUnless { it.isOutlivedBy(tail, idleForMillis) }
    if (reported != null) return reported
    if (!isRunning) return SessionAttention.None

    return if (tail == TranscriptTail.AgentsTurn) SessionAttention.Working else SessionAttention.WaitingForInput
}

/**
 * An interrupt never runs the hook that would clear a mid-turn state, so the transcript has to: its
 * interrupt marker ends the turn, and a transcript standing still for long enough says the same.
 */
private fun SessionAttention.isOutlivedBy(tail: TranscriptTail?, idleForMillis: Long): Boolean {
    val isMidTurn = this == SessionAttention.Working || this == SessionAttention.NeedsPermission
    if (isMidTurn && tail == TranscriptTail.Interrupted) return true

    return this == SessionAttention.Working && idleForMillis > WORKING_GOES_STALE_MS
}

/**
 * Long enough that a genuinely long turn is never mistaken for an abandoned one.
 *
 * Working now lasts a whole turn rather than a single tool call, and it is only ever cleared by the
 * hook that fires when the turn ends. A turn killed before that - the process gone, the keystroke
 * interrupted - would otherwise report working for as long as the session existed. Measured against
 * the transcript, which a working agent writes to on every tool result.
 */
const val WORKING_GOES_STALE_MS = 600_000L

fun sessionAttentionOf(notifyState: String?): SessionAttention = when {
    notifyState == null -> SessionAttention.None
    notifyState == "permission_prompt" -> SessionAttention.NeedsPermission
    notifyState == "idle_prompt" -> SessionAttention.WaitingForInput
    notifyState == "compact" -> SessionAttention.Compacting
    notifyState == "working" -> SessionAttention.Working
    notifyState.startsWith("tool:") -> SessionAttention.Working
    else -> SessionAttention.None
}
