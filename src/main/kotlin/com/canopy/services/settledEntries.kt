package com.canopy.services

/** A transcript untouched for this long is one an agent has stopped writing to. */
const val SETTLED_AFTER_MS = 5 * 60 * 1000L

/**
 * The entries a restart could still use.
 *
 * A digest is validated against its transcript's size and timestamp, so one for a file still being
 * appended to will not match by the time it is read back. Keeping it out is what stops the file
 * being rewritten every time an agent says anything.
 */
fun settledEntries(entries: List<SessionDigestCache.Entry>, nowMillis: Long): List<SessionDigestCache.Entry> =
    entries.filter { nowMillis - it.modifiedAtMillis >= SETTLED_AFTER_MS }
