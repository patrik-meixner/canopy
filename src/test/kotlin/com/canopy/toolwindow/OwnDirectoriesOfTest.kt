package com.canopy.toolwindow

import kotlin.test.Test
import kotlin.test.assertEquals

class OwnDirectoriesOfTest {

    private val directories = setOf("/launch", "/other/src")

    private fun own(launch: String?, written: Set<String>) = ownDirectoriesOf(launch, written) { it in directories }

    @Test
    fun `the launch directory is the session's`() {
        assertEquals(listOf("/launch"), own("/launch", emptySet()))
    }

    @Test
    fun `a directory the shell wrote in is the session's`() {
        assertEquals(listOf("/launch", "/other/src"), own("/launch", setOf("/other/src")))
    }

    @Test
    fun `a file written elsewhere claims nothing around it`() {
        assertEquals(listOf("/launch"), own("/launch", setOf("/other/src/X.kt")))
    }

    @Test
    fun `without a launch directory only what was written in counts`() {
        assertEquals(listOf("/other/src"), own(null, setOf("/other/src")))
    }
}
