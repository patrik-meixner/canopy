package com.canopy.services

enum class SidebarAction { Reopen, Record, Ignore }

/**
 * The IDE applies its own layout a moment after a project opens and closes the sidebar we just
 * restored, so until that has settled a closed sidebar is the platform's doing and not the user's.
 */
fun sidebarAction(isSettling: Boolean, wasOpen: Boolean, isVisible: Boolean): SidebarAction = when {
    !isSettling -> SidebarAction.Record
    wasOpen && !isVisible -> SidebarAction.Reopen
    else -> SidebarAction.Ignore
}
