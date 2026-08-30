package com.canopy.toolwindow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTooltipTest {

    @Test
    fun `a pasted banner is cut to a readable block`() {
        val banner = (1..40).joinToString("\n") { "line $it that goes on and on and on for a while" }
        val tooltip = tooltipFor(banner)

        assertEquals(6, tooltip.split("<br>").size - 1)
        assertTrue(tooltip.startsWith("<html>"))
        assertTrue(tooltip.endsWith("…</html>"))
    }

    @Test
    fun `a long single line is wrapped rather than drawn off screen`() {
        val tooltip = tooltipFor("x".repeat(300))

        assertTrue(tooltip.contains("<br>"))
    }

    @Test
    fun `a short prompt is left alone`() {
        assertEquals("<html>fix the build</html>", tooltipFor("fix the build"))
    }

    @Test
    fun `markup in a prompt cannot break the tooltip`() {
        assertEquals("<html>&lt;b&gt;bold&lt;/b&gt;</html>", tooltipFor("<b>bold</b>"))
    }
}
