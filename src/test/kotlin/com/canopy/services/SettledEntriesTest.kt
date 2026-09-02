package com.canopy.services

import org.junit.Assert.assertEquals
import org.junit.Test

class SettledEntriesTest {

    private fun entry(path: String, modifiedAtMillis: Long) =
        SessionDigestCache.Entry().apply {
            this.path = path
            this.modifiedAtMillis = modifiedAtMillis
        }

    @Test
    fun `a transcript still being written is not kept`() {
        val now = 1_000_000L

        val kept = settledEntries(listOf(entry("live", now - 1_000)), now)

        assertEquals(emptyList<String>(), kept.map { it.path })
    }

    @Test
    fun `a transcript nobody has touched for long enough is kept`() {
        val now = 1_000_000L

        val kept = settledEntries(listOf(entry("old", now - SETTLED_AFTER_MS)), now)

        assertEquals(listOf("old"), kept.map { it.path })
    }

    @Test
    fun `one working session does not take the others out with it`() {
        val now = 1_000_000L
        val entries = listOf(entry("live", now), entry("old", now - SETTLED_AFTER_MS * 2))

        assertEquals(listOf("old"), settledEntries(entries, now).map { it.path })
    }
}
