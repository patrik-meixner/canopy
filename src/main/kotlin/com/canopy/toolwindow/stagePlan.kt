package com.canopy.toolwindow

/** A Canopy tab as staging sees it: what it is, whose it is, and whether it is on screen now. */
data class StageTab(val key: String, val isShell: Boolean, val ownerKey: String?, val isOpen: Boolean)

/** The tabs to close and the tabs to open, as keys, to put [targetKey] on the stage. */
data class StagePlan(val close: List<String>, val open: List<String>)

/**
 * A session brings its terminals with it, in both directions: they belong to it, so showing it
 * without them would leave half a workspace, and leaving them behind when it goes would leave
 * shells hanging under a session that is no longer there.
 */
fun stagePlan(tabs: List<StageTab>, targetKey: String, isAdditive: Boolean): StagePlan {
    val staying = tabs.filter { it.key == targetKey || it.ownerKey == targetKey }
    val leaving = if (isAdditive) emptyList() else tabs - staying.toSet()

    return StagePlan(
        close = leaving.filter { it.isOpen }.map { it.key },
        open = staying.filterNot { it.isOpen }.map { it.key }
    )
}
