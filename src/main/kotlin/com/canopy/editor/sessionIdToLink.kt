package com.canopy.editor

/**
 * Claude Code reports the session its terminal is showing in every status line, and that id is not
 * always the one the tab started: resuming a conversation rotates it, and `/resume` replaces it.
 */
fun sessionIdToLink(reported: String?, linked: String?): String? =
    reported?.takeIf { it.isNotBlank() && it != linked }
