package com.canopy.settings

/**
 * Asking for a profile is a one-off; the session still comes back on whatever it last ran under.
 * A name that no longer resolves runs nothing rather than quietly billing another account.
 */
fun profileToRun(chosen: String?, remembered: String?, known: List<ClaudeProfile>): ClaudeProfile? {
    val wanted = chosen ?: remembered ?: return null

    return known.firstOrNull { it.name == wanted }
}
