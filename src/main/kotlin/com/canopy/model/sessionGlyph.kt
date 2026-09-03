package com.canopy.model

import com.canopy.toolwindow.spinnerFrame

enum class SessionPresence { OpenHere, OpenElsewhere, Available, Unresponsive }

fun sessionGlyph(attention: SessionAttention, presence: SessionPresence, nowMillis: Long): String? = when {
    presence == SessionPresence.Unresponsive -> "⊘"
    attention == SessionAttention.Working || attention == SessionAttention.Compacting -> spinnerFrame(nowMillis)
    attention != SessionAttention.None -> attention.glyph
    presence == SessionPresence.OpenElsewhere -> "↗"
    presence == SessionPresence.Available -> "○"
    else -> null
}
