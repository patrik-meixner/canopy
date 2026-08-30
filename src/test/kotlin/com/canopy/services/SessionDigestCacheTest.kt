package com.canopy.services

import com.canopy.model.SessionDisplay
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionDigestCacheTest {

    private val session = SessionDisplay(
        sessionId = "s1",
        name = "review",
        firstPrompt = "check the diff",
        messageCount = 12,
        modified = Instant.ofEpochMilli(777),
        gitBranch = "main",
        projectPath = "/ws",
        touchedRoots = mapOf("/ws/dmm" to 3)
    )

    private fun cacheWithEntry(): SessionDigestCache =
        SessionDigestCache().apply { put("/t/s1.jsonl", size = 100, modifiedAtMillis = 1_000, session = session) }

    @Test
    fun `returns the stored session when the file is untouched`() {
        val stored = cacheWithEntry().get("/t/s1.jsonl", size = 100, modifiedAtMillis = 1_000, projectPath = "/ws")

        assertEquals("review", stored?.name)
        assertEquals(mapOf("/ws/dmm" to 3), stored?.touchedRoots)
    }

    @Test
    fun `keeps the transcript's own timestamp, not the file's`() {
        val stored = cacheWithEntry().get("/t/s1.jsonl", size = 100, modifiedAtMillis = 1_000, projectPath = "/ws")

        assertEquals(Instant.ofEpochMilli(777), stored?.modified)
    }

    @Test
    fun `refuses the stored session once the transcript grew`() {
        val stored = cacheWithEntry().get("/t/s1.jsonl", size = 250, modifiedAtMillis = 1_000, projectPath = "/ws")

        assertNull(stored)
    }

    @Test
    fun `refuses the stored session once the transcript was rewritten`() {
        val stored = cacheWithEntry().get("/t/s1.jsonl", size = 100, modifiedAtMillis = 2_000, projectPath = "/ws")

        assertNull(stored)
    }

    @Test
    fun `knows nothing about a transcript it never saw`() {
        assertNull(cacheWithEntry().get("/t/other.jsonl", size = 100, modifiedAtMillis = 1_000, projectPath = "/ws"))
    }

    @Test
    fun `forgets transcripts that are gone`() {
        val cache = cacheWithEntry()

        cache.retainOnly(emptySet())

        assertNull(cache.get("/t/s1.jsonl", size = 100, modifiedAtMillis = 1_000, projectPath = "/ws"))
    }
}
