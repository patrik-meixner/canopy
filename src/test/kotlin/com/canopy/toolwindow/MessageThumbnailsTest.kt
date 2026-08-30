package com.canopy.toolwindow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageThumbnailsTest {

    @Test
    fun `a landscape image is bounded by its width`() {
        assertTrue(boundedWidth(800, 400, 96) > boundedHeight(800, 400, 96))
        assertEquals(boundedWidth(800, 400, 96) / 2, boundedHeight(800, 400, 96))
    }

    @Test
    fun `a portrait image is bounded by its height`() {
        assertTrue(boundedHeight(400, 800, 96) > boundedWidth(400, 800, 96))
        assertEquals(boundedHeight(400, 800, 96) / 2, boundedWidth(400, 800, 96))
    }

    @Test
    fun `an extreme aspect ratio never collapses to nothing`() {
        assertTrue(boundedWidth(2000, 1, 96) >= 1)
        assertTrue(boundedHeight(1, 2000, 96) >= 1)
    }
}
