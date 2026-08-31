package com.canopy.toolwindow

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageRepeatsTest {

    private fun message(ordinal: Int, text: String, images: List<String> = emptyList()) =
        SessionMessage(ordinal, text, images)

    @Test
    fun `an interrupted message recorded twice shows once`() {
        val messages = listOf(message(1, "why is this slow"), message(2, "why is this slow"))

        assertEquals(listOf(1), withoutRepeats(messages).map { it.ordinal })
    }

    @Test
    fun `the same question asked again later is not a repeat`() {
        val messages = listOf(message(1, "again"), message(2, "something else"), message(3, "again"))

        assertEquals(listOf(1, 2, 3), withoutRepeats(messages).map { it.ordinal })
    }

    @Test
    fun `a resend that carried an image is kept, since the image is new content`() {
        val messages = listOf(message(1, "look"), message(2, "look", listOf("AAAB")))

        assertEquals(listOf(1, 2), withoutRepeats(messages).map { it.ordinal })
    }

    @Test
    fun `an empty history stays empty`() {
        assertEquals(emptyList<SessionMessage>(), withoutRepeats(emptyList()))
    }
}
