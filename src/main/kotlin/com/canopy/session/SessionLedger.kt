package com.canopy.session

import java.nio.file.Files
import java.nio.file.Path

/**
 * The ledger the hooks keep for each session, outside the transcript and outside the IDE.
 *
 * A session started elsewhere has none, and the review then falls back to reading the transcript.
 */
object SessionLedger {

    fun directory(): Path = Path.of(System.getProperty("user.home"), ".local", "share", "canopy", "ledger")

    fun read(sessionId: String): LedgerEvidence? {
        val file = directory().resolve("$sessionId.ledger")
        if (!Files.isRegularFile(file)) return null

        return runCatching { Files.lines(file).use { parseLedger(it.iterator().asSequence()) } }.getOrNull()
    }
}
