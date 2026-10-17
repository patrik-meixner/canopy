package com.canopy.terminal

import kotlin.test.Test
import kotlin.test.assertTrue

class WarmUpPtyTest {

    @Test
    fun `the native pty library is loaded on the thread that asks for it`() {
        assertTrue(warmUpPty())
    }

    @Test
    fun `warming a library that is already loaded costs nothing and still reports it`() {
        warmUpPty()

        assertTrue(warmUpPty())
    }
}
