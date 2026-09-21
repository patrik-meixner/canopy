package com.canopy.insight

import kotlin.test.Test
import kotlin.test.assertEquals

class ClaimCardsTest {

    private val built = mutableListOf<String>()

    private fun claim(keys: List<String>, held: Map<String, String>) =
        claimCards(keys, held) { key -> built.add(key); "new-$key" }

    @Test
    fun `a card already on screen is kept rather than built again`() {
        assertEquals(listOf("old-a", "old-b"), claim(listOf("a", "b"), mapOf("a" to "old-a", "b" to "old-b")))
        assertEquals(emptyList(), built)
    }

    @Test
    fun `only what arrived since the last render is built`() {
        assertEquals(listOf("old-a", "new-b"), claim(listOf("a", "b"), mapOf("a" to "old-a")))
        assertEquals(listOf("b"), built)
    }

    @Test
    fun `a key listed twice gets a card of its own the second time`() {
        assertEquals(listOf("old-a", "new-a"), claim(listOf("a", "a"), mapOf("a" to "old-a")))
        assertEquals(listOf("a"), built)
    }

    @Test
    fun `cards that dropped off the list are not handed to anyone`() {
        assertEquals(listOf("new-c"), claim(listOf("c"), mapOf("a" to "old-a", "b" to "old-b")))
    }

    @Test
    fun `order follows the keys, not what was held`() {
        assertEquals(listOf("old-b", "old-a"), claim(listOf("b", "a"), mapOf("a" to "old-a", "b" to "old-b")))
    }
    @Test
    fun `a transcript that grows builds each card once, not the whole list again`() {
        var held = emptyMap<String, String>()
        val builds = mutableListOf<String>()

        repeat(200) { refresh ->
            val keys = (0..refresh).map { "m$it" }
            val cards = claimCards(keys, held) { key -> builds.add(key); "card-$key" }
            held = keys.zip(cards).toMap()
        }

        assertEquals(200, builds.size)
        assertEquals(builds.size, builds.distinct().size)
    }

}
