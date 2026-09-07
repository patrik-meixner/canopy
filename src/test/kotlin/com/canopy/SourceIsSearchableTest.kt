package com.canopy

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class SourceIsSearchableTest {

    @Test
    fun `no source file carries a byte that makes search tools treat it as binary`() {
        val offenders = File("src").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { 0.toByte() in it.readBytes() }
            .map { it.path }
            .toList()

        assertEquals(emptyList(), offenders)
    }
}
