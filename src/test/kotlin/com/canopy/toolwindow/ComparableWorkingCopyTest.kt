package com.canopy.toolwindow

import kotlin.test.Test
import kotlin.test.assertEquals

class ComparableWorkingCopyTest {

    @Test
    fun `a working copy git stores without carriage returns is compared without them`() {
        assertEquals(
            "one\nCHANGED\n",
            comparableWorkingCopy(committed = "one\ntwo\n", working = "one\r\nCHANGED\r\n")
        )
    }

    @Test
    fun `a file git keeps carriage returns in is left exactly as it is`() {
        assertEquals(
            "one\r\nCHANGED\r\n",
            comparableWorkingCopy(committed = "one\r\ntwo\r\n", working = "one\r\nCHANGED\r\n")
        )
    }

    @Test
    fun `a working copy that has no carriage returns is untouched`() {
        assertEquals(
            "one\ntwo\n",
            comparableWorkingCopy(committed = "one\ntwo\n", working = "one\ntwo\n")
        )
    }

    @Test
    fun `a lone carriage return is content, not a line ending`() {
        assertEquals(
            "one\rtwo\n",
            comparableWorkingCopy(committed = "one\ntwo\n", working = "one\rtwo\n")
        )
    }

    @Test
    fun `a new file has nothing to be compared against and keeps its bytes`() {
        assertEquals(
            "one\r\ntwo\r\n",
            comparableWorkingCopy(committed = null, working = "one\r\ntwo\r\n")
        )
    }
}
