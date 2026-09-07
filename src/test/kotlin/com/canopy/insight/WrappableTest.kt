package com.canopy.insight

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WrappableTest {

    @Test
    fun `a single line long enough to stall the layout is cut`() {
        val paragraph = "slovo ".repeat(20_000)

        assertTrue(wrappable(paragraph, maxLines = 4, maxCharacters = 400).length <= 401)
    }

    @Test
    fun `text that fits is left exactly as it is`() {
        assertEquals("krátká věta", wrappable("krátká věta", maxLines = 4, maxCharacters = 400))
    }

    @Test
    fun `what is cut says so`() {
        assertTrue(wrappable("x".repeat(500), maxLines = 4, maxCharacters = 400).endsWith("…"))
    }

    @Test
    fun `the line budget still applies to text made of short lines`() {
        val lines = (1..40).joinToString("\n") { "line $it" }

        assertEquals(4, wrappable(lines, maxLines = 4, maxCharacters = 400).lineSequence().count { it != "…" })
    }

    @Test
    fun `blank lines are not what the budget is spent on`() {
        val padded = "first\n\n\n\nsecond"

        assertEquals("first\nsecond", wrappable(padded, maxLines = 4, maxCharacters = 400))
    }
}
