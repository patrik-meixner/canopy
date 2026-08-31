package com.canopy.toolwindow

import org.junit.Assert.assertEquals
import org.junit.Test

class WithinFileBudgetTest {

    private fun budget(files: List<Int>, budget: Int) = withinFileBudget(files, budget) { it }

    @Test
    fun `everything is kept when it fits`() {
        val budgeted = budget(listOf(3, 4), 100)

        assertEquals(listOf(3, 4), budgeted.kept)
        assertEquals(0, budgeted.omitted)
        assertEquals(0, budgeted.omittedFiles)
    }

    @Test
    fun `the newest commits win the budget and the rest are counted`() {
        val budgeted = budget(listOf(6, 7, 8), 5)

        assertEquals(listOf(6), budgeted.kept)
        assertEquals(2, budgeted.omitted)
        assertEquals(15, budgeted.omittedFiles)
    }

    @Test
    fun `one commit larger than the whole budget is still shown`() {
        val budgeted = budget(listOf(900), 100)

        assertEquals(listOf(900), budgeted.kept)
        assertEquals(0, budgeted.omitted)
    }
}
