package com.canopy.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ParseLedgerTest {

    private val evidence = parseLedger(
        sequenceOf(
            "1000\tW\t/ws/canopy\t/ws/canopy/src/A.kt",
            "1000\tW\t/ws/canopy\t/ws/canopy/src/B.kt",
            "2000\tD\t/ws/canopy\t/ws/canopy/src/Old.kt",
            "3000\tC\t/ws/dmm\tabc123",
            "garbage line",
            "4000\tX\t/ws/other\t/ws/other/x"
        )
    )

    @Test
    fun `a written or deleted file is the session's, anything else is not`() {
        assertTrue(evidence.owns("/ws/canopy/src/A.kt"))
        assertTrue(evidence.owns("/ws/canopy/src/Old.kt"))
        assertFalse(evidence.owns("/ws/canopy/src/C.kt"))
    }

    @Test
    fun `commits are kept by sha`() {
        assertEquals(setOf("abc123"), evidence.commits)
    }

    @Test
    fun `every repository with a record is in scope, an unknown kind is not`() {
        assertEquals(setOf("/ws/canopy", "/ws/dmm"), evidence.roots)
    }
}
