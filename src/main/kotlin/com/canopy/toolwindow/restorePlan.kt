package com.canopy.toolwindow

import com.canopy.model.SessionDisplay
import com.canopy.settings.SessionRestore

data class RestorePlan(val staged: SessionDisplay?, val detached: List<SessionDisplay>)

fun restorePlan(saved: List<SessionDisplay>, mode: SessionRestore): RestorePlan {
    if (mode == SessionRestore.None) return RestorePlan(null, emptyList())

    val staged = saved.firstOrNull() ?: return RestorePlan(null, emptyList())
    val detached = if (mode == SessionRestore.All) saved.drop(1) else emptyList()

    return RestorePlan(staged, detached)
}
