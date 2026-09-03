package com.canopy.toolwindow

data class StageTab(val key: String, val isShell: Boolean, val ownerKey: String?, val isOpen: Boolean)

data class StagePlan(val close: List<String>, val open: List<String>)

fun stagePlan(tabs: List<StageTab>, targetKey: String, isAdditive: Boolean): StagePlan {
    val staying = tabs.filter { it.key == targetKey || it.ownerKey == targetKey }
    val leaving = if (isAdditive) emptyList() else tabs - staying.toSet()

    return StagePlan(
        close = leaving.filter { it.isOpen }.map { it.key },
        open = staying.filterNot { it.isOpen }.map { it.key }
    )
}
