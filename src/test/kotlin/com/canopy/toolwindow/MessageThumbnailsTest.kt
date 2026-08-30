package com.canopy.toolwindow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageThumbnailsTest {

    @Test
    fun `a landscape image is bounded by its width`() {
        assertTrue(scaledWidth(800, 400) > scaledHeight(800, 400))
        assertEquals(scaledWidth(800, 400) / 2, scaledHeight(800, 400))
    }

    @Test
    fun `a portrait image is bounded by its height`() {
        assertTrue(scaledHeight(400, 800) > scaledWidth(400, 800))
        assertEquals(scaledHeight(400, 800) / 2, scaledWidth(400, 800))
    }

    @Test
    fun `an extreme aspect ratio never collapses to nothing`() {
        assertTrue(scaledWidth(2000, 1) >= 1)
        assertTrue(scaledHeight(1, 2000) >= 1)
    }
}
