package com.canopy.util

import kotlin.test.Test
import kotlin.test.assertEquals

class DeferredRefreshTest {

    private var seen = true
    private val runs = mutableListOf<Boolean>()
    private val refresh = DeferredRefresh({ seen }) { runs += it }

    @Test
    fun `a request while seen runs at once`() {
        refresh.request(isForced = true)

        assertEquals(listOf(true), runs)
    }

    @Test
    fun `a request while unseen runs nothing`() {
        seen = false
        refresh.request()
        refresh.request()

        assertEquals(emptyList(), runs)
    }

    @Test
    fun `coming back after missed requests runs exactly once`() {
        seen = false
        refresh.request()
        refresh.request()
        seen = true
        refresh.catchUp()
        refresh.catchUp()

        assertEquals(listOf(false), runs)
    }

    @Test
    fun `coming back with nothing missed runs nothing`() {
        refresh.catchUp()

        assertEquals(emptyList(), runs)
    }

    @Test
    fun `catching up while still unseen keeps the request owed`() {
        seen = false
        refresh.request()
        refresh.catchUp()
        seen = true
        refresh.catchUp()

        assertEquals(listOf(false), runs)
    }

    @Test
    fun `a catch-up can be told to skip the floor`() {
        val forced = mutableListOf<Boolean>()
        val refresh = DeferredRefresh({ seen }, catchUpForced = true) { forced += it }
        seen = false
        refresh.request()
        seen = true
        refresh.catchUp()

        assertEquals(listOf(true), forced)
    }
}
