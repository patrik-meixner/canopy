package com.canopy.toolwindow

import java.nio.file.Files
import java.nio.file.Path

data class RootFingerprint(
    val gitState: Long,
    val ledgerModified: Long,
    val ledgerSize: Long,
    val newestWrite: Long,
    val existingWrites: Int
) {
    companion object {
        fun of(root: String, ledger: Path, written: Collection<String>): RootFingerprint {
            val writtenHere = written.filter { it.startsWith("$root/") }.mapNotNull(::lastModifiedOrNull)

            return RootFingerprint(
                gitState = com.canopy.services.gitStateStamp(root),
                ledgerModified = lastModifiedOrNull(ledger.toString()) ?: 0L,
                ledgerSize = runCatching { Files.size(ledger) }.getOrDefault(0L),
                newestWrite = writtenHere.maxOrNull() ?: 0L,
                existingWrites = writtenHere.size
            )
        }

        private fun lastModifiedOrNull(path: String): Long? =
            runCatching { Files.getLastModifiedTime(Path.of(path)).toMillis() }.getOrNull()
    }
}
