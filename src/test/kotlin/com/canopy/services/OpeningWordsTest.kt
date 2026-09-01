package com.canopy.services

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpeningWordsTest {

    @Test
    fun `a short prompt is kept whole`() {
        assertEquals("fix the build", openingWords("fix the build"))
    }

    @Test
    fun `a pasted essay is cut to what anything reads`() {
        val kept = openingWords("x".repeat(569_184))

        assertTrue(kept.length < 1_000, "kept ${kept.length} characters")
    }

    @Test
    fun `what the list shows survives the cut`() {
        val prompt = "Refactor the settlement engine so the remainder lands on the pool owner" + " x".repeat(9_000)

        assertTrue(openingWords(prompt).startsWith(prompt.take(60)))
    }
}
