package com.canopy.toolwindow

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DraftRowsTest {

    private val now = 1_700_000_000_000

    @Test
    fun `an open tab that has not started is a row of its own`() {
        val rows = draftRows(listOf(DraftSource("new-1", "New session")), emptySet(), "/ws", now)

        assertEquals(listOf("new-1"), rows.map { it.sessionId })
        assertEquals("New session", rows.single().displayName)
        assertTrue(rows.single().isDraft)
    }

    @Test
    fun `nothing open means no rows to add`() {
        assertEquals(emptyList(), draftRows(emptyList(), emptySet(), "/ws", now))
    }

    @Test
    fun `a draft has nothing said in it yet`() {
        val row = draftRows(listOf(DraftSource("new-1", "New session")), emptySet(), "/ws", now).single()

        assertEquals(0, row.messageCount)
        assertEquals(null, row.startedAt)
    }

    @Test
    fun `a draft whose session the list already shows stops being a row beside it`() {
        val started = DraftSource("new-1", "New session", reported = "s1")

        assertEquals(emptyList(), draftRows(listOf(started), setOf("s1"), "/ws", now))
    }

    @Test
    fun `a draft whose session has not reached the list keeps its row`() {
        val started = DraftSource("new-1", "New session", reported = "s2")

        assertEquals(listOf("new-1"), draftRows(listOf(started), setOf("s1"), "/ws", now).map { it.sessionId })
    }

    @Test
    fun `a tab that has started nothing keeps its row whatever the list holds`() {
        val idle = DraftSource("new-1", "New session")

        assertEquals(listOf("new-1"), draftRows(listOf(idle), setOf("s1"), "/ws", now).map { it.sessionId })
    }
}

class DraftMetaTest {

    @Test
    fun `a draft says it has not started, not where or when it ran`() {
        val draft = draftRows(listOf(DraftSource("new-1", "New session")), emptySet(), "/ws/urbido", 1_700_000_000_000).single()

        assertEquals("Not started yet", metaLine(draft, "", 1_700_000_000_000))
    }
}
