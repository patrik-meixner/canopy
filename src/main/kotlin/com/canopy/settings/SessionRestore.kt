package com.canopy.settings

/** What opening a project does about the sessions that were running when it was last closed. */
enum class SessionRestore(val label: String) {
    LastOnly("Only the last session"),
    All("Every session that was running"),
    None("Nothing")
}
