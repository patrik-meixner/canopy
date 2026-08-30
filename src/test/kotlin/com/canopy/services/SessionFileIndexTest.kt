package com.canopy.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionFileIndexTest {

    @Test
    fun `a file two sessions wrote is reported with the other session`() {
        val index = mapOf(
            "a" to setOf("/ws/one.ts", "/ws/two.ts"),
            "b" to setOf("/ws/two.ts")
        )

        assertEquals(mapOf("/ws/two.ts" to listOf("b")), overlaps("a", index))
    }

    @Test
    fun `a file only this session wrote is not an overlap`() {
        val index = mapOf("a" to setOf("/ws/one.ts"), "b" to setOf("/ws/other.ts"))

        assertTrue(overlaps("a", index).isEmpty())
    }

    @Test
    fun `every session sharing the file is named`() {
        val index = mapOf(
            "a" to setOf("/ws/shared.ts"),
            "b" to setOf("/ws/shared.ts"),
            "c" to setOf("/ws/shared.ts")
        )

        assertEquals(listOf("b", "c"), overlaps("a", index)["/ws/shared.ts"])
    }

    @Test
    fun `a session the index has never seen has no overlaps`() {
        assertTrue(overlaps("gone", mapOf("a" to setOf("/ws/one.ts"))).isEmpty())
    }
}
