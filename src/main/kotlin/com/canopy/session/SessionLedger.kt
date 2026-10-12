package com.canopy.session

import java.nio.file.Files
import java.nio.file.Path

object SessionLedger {

    fun directory(): Path = Path.of(System.getProperty("user.home"), ".local", "share", "canopy", "ledger")

    fun fileOf(sessionId: String): Path = directory().resolve("$sessionId.ledger")

    fun read(sessionId: String): LedgerEvidence? {
        val file = fileOf(sessionId)
        if (!Files.isRegularFile(file)) return null

        return runCatching { Files.lines(file).use { parseLedger(it.iterator().asSequence()) } }.getOrNull()
    }
}
