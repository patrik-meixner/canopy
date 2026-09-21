package com.canopy.toolwindow

/** Only worth a line when the session is not on the account everything else already uses. */
internal fun profileNote(profile: String?, default: String?): String? =
    profile?.takeIf { it != default }
