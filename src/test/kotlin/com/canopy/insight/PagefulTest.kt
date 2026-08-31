package com.canopy.insight

import org.junit.Assert.assertEquals
import org.junit.Test

class PagefulTest {

    @Test
    fun `a short list is shown whole and nothing is left`() {
        val page = pageful(listOf(1, 2, 3), 10)

        assertEquals(listOf(1, 2, 3), page.shown)
        assertEquals(0, page.remaining)
    }

    @Test
    fun `a long list is cut and the rest is counted`() {
        val page = pageful((1..10).toList(), 4)

        assertEquals(listOf(1, 2, 3, 4), page.shown)
        assertEquals(6, page.remaining)
    }

    @Test
    fun `a list exactly the size of the page has nothing behind it`() {
        assertEquals(0, pageful(listOf(1, 2), 2).remaining)
    }
}
