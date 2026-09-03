package com.canopy.services

import kotlin.test.Test
import kotlin.test.assertEquals

class SidebarActionTest {

    @Test
    fun `the platform closing the sidebar while it settles is undone, not recorded`() {
        assertEquals(
            SidebarAction.Reopen,
            sidebarAction(isSettling = true, wasOpen = true, isVisible = false)
        )
    }

    @Test
    fun `a sidebar that survived the settling is left alone`() {
        assertEquals(
            SidebarAction.Ignore,
            sidebarAction(isSettling = true, wasOpen = true, isVisible = true)
        )
    }

    @Test
    fun `nothing is put back that was not remembered open`() {
        assertEquals(
            SidebarAction.Ignore,
            sidebarAction(isSettling = true, wasOpen = false, isVisible = false)
        )
    }

    @Test
    fun `once it has settled the sidebar is the user's again`() {
        assertEquals(
            SidebarAction.Record,
            sidebarAction(isSettling = false, wasOpen = true, isVisible = false)
        )
        assertEquals(
            SidebarAction.Record,
            sidebarAction(isSettling = false, wasOpen = false, isVisible = true)
        )
    }
}
