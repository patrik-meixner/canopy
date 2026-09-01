package com.canopy.toolwindow

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DraftRowsTest {

    private val now = 1_700_000_000_000

    @Test
    fun `an open tab that has not started is a row of its own`() {
        val rows = draftRows(listOf(DraftSource("new-1", "New session")), "/ws", now)

        assertEquals(listOf("new-1"), rows.map { it.sessionId })
        assertEquals("New session", rows.single().displayName)
        assertTrue(rows.single().isDraft)
    }

    @Test
    fun `nothing open means no rows to add`() {
        assertEquals(emptyList(), draftRows(emptyList(), "/ws", now))
    }

    @Test
    fun `a draft has nothing said in it yet`() {
        val row = draftRows(listOf(DraftSource("new-1", "New session")), "/ws", now).single()

        assertEquals(0, row.messageCount)
        assertEquals(null, row.startedAt)
    }
}

class DraftMetaTest {

    @Test
    fun `a draft says it has not started, not where or when it ran`() {
        val draft = draftRows(listOf(DraftSource("new-1", "New session")), "/ws/urbido", 1_700_000_000_000).single()

        assertEquals("Not started yet", metaLine(draft, "", 1_700_000_000_000))
    }
}
