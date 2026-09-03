package com.canopy.model

enum class SessionAttention(val rank: Int, val glyph: String) {
    NeedsPermission(0, "!"),
    WaitingForInput(1, "›"),
    Working(2, "*"),
    Compacting(2, "~"),
    None(3, "");

    val isBlocking: Boolean get() = this == NeedsPermission || this == WaitingForInput
}

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

/** A turn killed before its Stop hook would report working forever; long enough that a real one never trips it. */
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
