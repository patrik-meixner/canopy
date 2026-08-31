package com.canopy.insight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FindScrollbackRowTest {

    @Test
    fun `a repeated prompt resolves to the most recent time it was said`() {
        val lines = listOf("run the tests", "output", "run the tests", "output")

        assertEquals(2, findScrollbackRow(lines, "run the tests"))
    }

    @Test
    fun `a message the terminal wrapped is still found`() {
        val lines = listOf("noise", "commit everything and ", "push it to origin")

        assertEquals(1, findScrollbackRow(lines, "commit everything and push"))
    }

    @Test
    fun `a message that is not in the scrollback reports nothing`() {
        assertNull(findScrollbackRow(listOf("noise"), "something else"))
    }

    @Test
    fun `an empty key matches nothing rather than the last row`() {
        assertNull(findScrollbackRow(listOf("noise"), ""))
    }
}
