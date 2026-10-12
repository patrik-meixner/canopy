package com.canopy.editor

import java.nio.file.Files
import java.nio.file.attribute.FileTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FileContentCacheTest {

    private val cache = FileContentCache()

    @Test
    fun `a file at the same HEAD is asked of git once`() {
        var asked = 0
        repeat(3) { cache.atHead("abc", "/ws/App.kt") { asked++; "head" } }

        assertEquals(1, asked)
    }

    @Test
    fun `a new HEAD asks git again`() {
        var asked = 0
        cache.atHead("abc", "/ws/App.kt") { asked++; "old" }

        assertEquals("new", cache.atHead("def", "/ws/App.kt") { asked++; "new" })
        assertEquals(2, asked)
    }

    @Test
    fun `a file on disk is reread once it changes`() {
        val file = Files.writeString(Files.createTempFile("canopy", ".kt"), "first")
        assertEquals("first", cache.onDisk(file.toString()))

        Files.writeString(file, "second!")
        Files.setLastModifiedTime(file, FileTime.fromMillis(Files.getLastModifiedTime(file).toMillis() + 5_000))

        assertEquals("second!", cache.onDisk(file.toString()))
    }

    @Test
    fun `a file that is gone reads as nothing`() {
        assertNull(cache.onDisk("/nonexistent/canopy/App.kt"))
    }
}
