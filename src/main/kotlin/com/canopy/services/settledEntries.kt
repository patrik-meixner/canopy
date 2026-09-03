package com.canopy.services

const val SETTLED_AFTER_MS = 5 * 60 * 1000L

fun settledEntries(entries: List<SessionDigestCache.Entry>, nowMillis: Long): List<SessionDigestCache.Entry> =
    entries.filter { nowMillis - it.modifiedAtMillis >= SETTLED_AFTER_MS }
