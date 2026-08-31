package com.canopy.util

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class DirectoryOfTest {

    @Test
    fun `a directory is where the work happened, not its parent`() {
        val directory = Files.createTempDirectory("canopy-demo")

        assertEquals(directory, directoryOf(directory))
    }

    @Test
    fun `a file names the directory it sits in`() {
        val directory = Files.createTempDirectory("canopy-demo")
        val file = Files.writeString(directory.resolve("rows.ts"), "")

        assertEquals(directory, directoryOf(file))
    }

    @Test
    fun `a path that is not on disk is treated as a file, which is what a transcript usually names`() {
        val directory = Files.createTempDirectory("canopy-demo")

        assertEquals(directory, directoryOf(directory.resolve("gone.ts")))
    }
}
