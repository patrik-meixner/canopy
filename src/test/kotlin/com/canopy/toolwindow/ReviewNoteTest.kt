package com.canopy.toolwindow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReviewNoteTest {

    private val roots = setOf("/ws", "/ws/dmm")

    @Test
    fun `one file with a selection carries the line range`() {
        val prompt = promptFor(ReviewNote(listOf("/ws/dmm/App.kt"), 20..30, "this leaks"), roots)

        assertEquals("In App.kt:20-30: this leaks\r", prompt)
    }

    @Test
    fun `a path is relative to its own repository, not the outermost one`() {
        assertEquals("App.kt", relativize("/ws/dmm/App.kt", roots))
        assertEquals("lib/Other.kt", relativize("/ws/lib/Other.kt", roots))
    }

    @Test
    fun `several files are listed, and a long list is summarised`() {
        val paths = (1..15).map { "/ws/File$it.kt" }
        val prompt = promptFor(ReviewNote(paths, null, "rename these"), roots)

        assertEquals(true, prompt!!.contains("and 3 more"))
    }

    @Test
    fun `an empty note sends nothing`() {
        assertNull(promptFor(ReviewNote(listOf("/ws/App.kt"), null, "   "), roots))
    }

    @Test
    fun `a note about no file sends nothing`() {
        assertNull(promptFor(ReviewNote(emptyList(), null, "look at this"), roots))
    }

    @Test
    fun `a file outside every repository still reads as a name`() {
        assertEquals("elsewhere.kt", relativize("/tmp/elsewhere.kt", roots))
    }
}
