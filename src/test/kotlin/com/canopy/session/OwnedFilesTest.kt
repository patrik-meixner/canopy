package com.canopy.session

import org.junit.Assert.assertEquals
import org.junit.Test

class OwnedFilesTest {

    private val head = mapOf("/a" to "a0", "/b" to "b0")

    private fun owned(changed: Set<String>, writes: List<FileWrite>, current: Map<String, String>) =
        ownedFiles(changed, writes, { head[it].orEmpty() }, { current[it] })

    @Test
    fun `a file the session wrote and nobody else touched is owned`() {
        val result = owned(setOf("/a"), listOf(FileWrite.Whole("/a", "a1")), mapOf("/a" to "a1"))

        assertEquals(listOf("/a"), result)
    }

    @Test
    fun `one file with an outside edit makes the whole set unsafe`() {
        val result = owned(
            setOf("/a", "/b"),
            listOf(FileWrite.Whole("/a", "a1"), FileWrite.Whole("/b", "b1")),
            mapOf("/a" to "a1", "/b" to "b1 plus someone else"),
        )

        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun `changed files the session never wrote are not its business`() {
        val result = owned(setOf("/b"), listOf(FileWrite.Whole("/a", "a1")), mapOf("/b" to "b9"))

        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun `a file that cannot be read is not owned, and neither is the set`() {
        val result = owned(setOf("/a"), listOf(FileWrite.Whole("/a", "a1")), emptyMap())

        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun `a new file has no head version to compare against`() {
        val result = ownedFiles(
            setOf("/new"),
            listOf(FileWrite.Whole("/new", "fresh")),
            { "" },
            { "fresh" },
        )

        assertEquals(listOf("/new"), result)
    }
}
