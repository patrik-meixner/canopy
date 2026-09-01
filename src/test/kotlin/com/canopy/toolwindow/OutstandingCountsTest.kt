package com.canopy.toolwindow

import kotlin.test.Test
import kotlin.test.assertEquals

class OutstandingCountsTest {

    @Test
    fun `only the states that have files in them are shown`() {
        val counts = outstandingCounts(uncommitted = 2, untracked = 0, unpushed = 3, pushed = 400)

        assertEquals(listOf("to commit", "to push"), counts.map { it.label })
        assertEquals(listOf(2, 3), counts.map { it.count })
    }

    @Test
    fun `a session with nothing left to do says what it already shipped`() {
        val counts = outstandingCounts(uncommitted = 0, untracked = 0, unpushed = 0, pushed = 12)

        assertEquals(listOf(OutstandingCount(12, "pushed", OutstandingTone.Clear)), counts)
    }

    @Test
    fun `work still to do never reads as clear`() {
        val counts = outstandingCounts(uncommitted = 0, untracked = 7, unpushed = 0, pushed = 400)

        assertEquals(listOf(OutstandingCount(7, "untracked", OutstandingTone.Untracked)), counts)
    }
}
