package com.canopy.model

import com.canopy.toolwindow.spinnerFrame

/** Where a session is, as opposed to what it is doing. */
enum class SessionPresence { OpenHere, OpenElsewhere, Available, Unresponsive }

/**
 * The one glyph for a session's state, wherever it is drawn.
 *
 * The list and the editor tab each had a vocabulary of their own, so the same session could be a
 * cog in one place and an asterisk in the other.
 *
 * [SessionPresence.OpenHere] draws nothing on purpose: in the list the row says where the session
 * is, and a tab is already the session being open here.
 */
fun sessionGlyph(attention: SessionAttention, presence: SessionPresence, nowMillis: Long): String? = when {
    presence == SessionPresence.Unresponsive -> "⊘"
    attention == SessionAttention.Working || attention == SessionAttention.Compacting -> spinnerFrame(nowMillis)
    attention != SessionAttention.None -> attention.glyph
    presence == SessionPresence.OpenElsewhere -> "↗"
    presence == SessionPresence.Available -> "○"
    else -> null
}
