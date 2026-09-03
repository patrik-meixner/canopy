package com.canopy.settings

enum class SessionRestore(val label: String) {
    LastOnly("Only the last session"),
    All("Every session that was running"),
    None("Nothing")
}
