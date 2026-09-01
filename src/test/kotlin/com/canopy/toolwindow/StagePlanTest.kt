package com.canopy.toolwindow

import org.junit.Assert.assertEquals
import org.junit.Test

class StagePlanTest {

    private fun session(key: String, isOpen: Boolean) = StageTab(key, isShell = false, ownerKey = null, isOpen = isOpen)

    private fun shell(key: String, owner: String, isOpen: Boolean) =
        StageTab(key, isShell = true, ownerKey = owner, isOpen = isOpen)

    private val tabs = listOf(
        session("a", isOpen = true),
        shell("a-be", "a", isOpen = true),
        session("b", isOpen = false),
        shell("b-fe", "b", isOpen = false)
    )

    @Test
    fun `staging a session opens it with its terminals and closes what was there`() {
        val plan = stagePlan(tabs, "b", isAdditive = false)

        assertEquals(listOf("a", "a-be"), plan.close)
        assertEquals(listOf("b", "b-fe"), plan.open)
    }

    @Test
    fun `adding a session leaves everything already on the stage alone`() {
        val plan = stagePlan(tabs, "b", isAdditive = true)

        assertEquals(emptyList<String>(), plan.close)
        assertEquals(listOf("b", "b-fe"), plan.open)
    }

    @Test
    fun `staging what is already staged closes and opens nothing`() {
        val plan = stagePlan(listOf(session("a", isOpen = true), shell("a-be", "a", isOpen = true)), "a", false)

        assertEquals(emptyList<String>(), plan.close)
        assertEquals(emptyList<String>(), plan.open)
    }

    @Test
    fun `a shell leaves with its own session, not with the one being staged`() {
        val open = listOf(
            session("a", isOpen = true),
            session("b", isOpen = true),
            shell("b-fe", "b", isOpen = true)
        )

        val plan = stagePlan(open, "a", isAdditive = false)

        assertEquals(listOf("b", "b-fe"), plan.close)
    }
}
