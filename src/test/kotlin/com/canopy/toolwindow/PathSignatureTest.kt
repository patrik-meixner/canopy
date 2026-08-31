package com.canopy.toolwindow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PathSignatureTest {

    @Test
    fun `the same paths in the same order agree`() {
        assertEquals(pathSignature(sequenceOf("a", "b")), pathSignature(sequenceOf("a", "b")))
    }

    @Test
    fun `a changed path is a different set`() {
        assertNotEquals(pathSignature(sequenceOf("a", "b")), pathSignature(sequenceOf("a", "c")))
    }

    @Test
    fun `a file moving between sections is a different set`() {
        assertNotEquals(pathSignature(sequenceOf("0:a")), pathSignature(sequenceOf("1:a")))
    }

    @Test
    fun `order carries meaning, because the tree renders in it`() {
        assertNotEquals(pathSignature(sequenceOf("a", "b")), pathSignature(sequenceOf("b", "a")))
    }
}
