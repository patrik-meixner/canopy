package com.canopy.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnippetTest {

    @Test
    fun `the match sits in the middle of its own context`() {
        val text = "a".repeat(200) + " needle " + "b".repeat(200)
        val snippet = snippetAround(text, "needle")

        assertTrue(snippet.contains("needle"))
        assertTrue(snippet.startsWith("…"))
        assertTrue(snippet.endsWith("…"))
    }

    @Test
    fun `a short message is shown whole with no ellipsis`() {
        assertEquals("fix the build", snippetAround("fix the build", "build"))
    }

    @Test
    fun `line breaks collapse, so a result is one scannable line`() {
        assertEquals("one two three", snippetAround("one\n\n  two\tthree", "two"))
    }

    @Test
    fun `a match that is not there still yields readable text`() {
        assertTrue(snippetAround("some text", "absent").isNotEmpty())
    }
}
