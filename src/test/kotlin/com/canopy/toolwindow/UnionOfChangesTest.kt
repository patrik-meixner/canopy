package com.canopy.toolwindow

import org.junit.Assert.assertEquals
import org.junit.Test

class UnionOfChangesTest {

    private data class Touched(val path: String, val fromCommit: String)

    private fun union(vararg touched: Touched) = unionByPath(touched.toList()) { it.path }

    @Test
    fun `a file touched by several commits is listed once`() {
        val union = union(Touched("/a", "newest"), Touched("/b", "newest"), Touched("/a", "older"))

        assertEquals(listOf("/a", "/b"), union.map { it.path })
    }

    @Test
    fun `the newest commit wins, because it is what the file looks like now`() {
        val union = union(Touched("/a", "newest"), Touched("/a", "older"))

        assertEquals(listOf("newest"), union.map { it.fromCommit })
    }

    @Test
    fun `an entry with no path at all is left out rather than grouped under nothing`() {
        val union = unionByPath(listOf("kept", "dropped")) { if (it == "kept") "/a" else null }

        assertEquals(listOf("kept"), union)
    }
}
