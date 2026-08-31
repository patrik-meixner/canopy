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
    WaitingForInput(1, "\u203A"),
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
fun sessionAttentionFor(notifyState: String?, isRunning: Boolean, lastEntryRole: String?): SessionAttention {
    if (notifyState != null) return sessionAttentionOf(notifyState)
    if (!isRunning) return SessionAttention.None

    return if (lastEntryRole == "assistant") SessionAttention.WaitingForInput else SessionAttention.Working
}

fun sessionAttentionOf(notifyState: String?): SessionAttention = when {
    notifyState == null -> SessionAttention.None
    notifyState == "permission_prompt" -> SessionAttention.NeedsPermission
    notifyState == "idle_prompt" -> SessionAttention.WaitingForInput
    notifyState == "compact" -> SessionAttention.Compacting
    notifyState.startsWith("tool:") -> SessionAttention.Working
    else -> SessionAttention.None
}
