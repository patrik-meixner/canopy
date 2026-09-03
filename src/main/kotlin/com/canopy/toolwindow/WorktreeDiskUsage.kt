package com.canopy.toolwindow

import com.canopy.util.ProcessHelper

object WorktreeDiskUsage {

    private const val TIMEOUT_MS = 20_000L

    fun measure(path: String): Long? {
        val result = ProcessHelper.execWithTimeout(arrayOf("du", "-sk", path), TIMEOUT_MS)
        if (result.exitCode != 0 && result.output.isBlank()) return null

        return parseDuKilobytes(result.output)?.times(1024)
    }
}

internal fun parseDuKilobytes(output: String): Long? =
    output.lineSequence()
        .mapNotNull { it.trim().substringBefore('\t').substringBefore(' ').toLongOrNull() }
        .firstOrNull()

internal fun formatSize(bytes: Long): String {
    val units = listOf("GB" to (1L shl 30), "MB" to (1L shl 20), "KB" to (1L shl 10))
    val (unit, scale) = units.firstOrNull { bytes >= it.second } ?: return "$bytes B"

    return String.format("%.1f %s", bytes.toDouble() / scale, unit)
}
