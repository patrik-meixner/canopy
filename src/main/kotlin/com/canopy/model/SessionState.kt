package com.canopy.model

/** What a session wants and where it is, which together decide the one glyph it is drawn with. */
data class SessionState(val attention: SessionAttention, val presence: SessionPresence)
