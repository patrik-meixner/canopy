package com.canopy.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayWritesTest {

    @Test
    fun `a whole-file write replaces whatever was there`() {
        val replayed = replayWrites("old", listOf(FileWrite.Whole("/a", "new")))

        assertEquals("new", replayed)
    }

    @Test
    fun `replacements apply in the order they were made`() {
        val replayed = replayWrites(
            "one two",
            listOf(FileWrite.Replace("/a", "one", "1"), FileWrite.Replace("/a", "two", "2")),
        )

        assertEquals("1 2", replayed)
    }

    @Test
    fun `only the first occurrence is replaced, as the tool does`() {
        val replayed = replayWrites("x x", listOf(FileWrite.Replace("/a", "x", "y")))

        assertEquals("y x", replayed)
    }

    @Test
    fun `a replacement whose target is gone cannot be replayed`() {
        assertNull(replayWrites("nothing like it", listOf(FileWrite.Replace("/a", "missing", "y"))))
    }

    @Test
    fun `a file the writes fully explain is accounted for`() {
        assertTrue(writesAccountFor("a", "b", listOf(FileWrite.Whole("/f", "b"))))
    }

    @Test
    fun `a file someone else also edited is not`() {
        assertFalse(writesAccountFor("a", "b and more", listOf(FileWrite.Whole("/f", "b"))))
    }

    @Test
    fun `no writes account for nothing, however the file looks`() {
        assertFalse(writesAccountFor("a", "a", emptyList()))
    }
}
