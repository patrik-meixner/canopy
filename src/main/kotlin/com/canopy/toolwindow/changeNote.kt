package com.canopy.toolwindow

/** What to say about a row whose diff will show nothing, so an empty pane is never a surprise. */
fun changeNote(entry: GitDiffEntry): String? {
    if (entry.hasContentChange) return null
    if (entry.isSubmodule) return "submodule"

    return "permissions only".takeIf { entry.modeChanged }
}
