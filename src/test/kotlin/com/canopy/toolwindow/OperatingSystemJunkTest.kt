package com.canopy.toolwindow

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OperatingSystemJunkTest {

    @Test
    fun `the finder's own files are junk wherever they sit`() {
        assertTrue(isOperatingSystemJunk(".DS_Store"))
        assertTrue(isOperatingSystemJunk("/ws/dtf/modules/app/.DS_Store"))
    }

    @Test
    fun `a file the session wrote is never junk`() {
        assertFalse(isOperatingSystemJunk("/ws/dtf/modules/app/Main.kt"))
    }

    @Test
    fun `a name that merely ends in one is left alone`() {
        assertFalse(isOperatingSystemJunk("/ws/notes/my.DS_Store.md"))
        assertFalse(isOperatingSystemJunk("/ws/tools/make.DS_Store"))
    }
}
