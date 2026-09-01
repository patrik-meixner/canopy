package com.canopy.insight

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

/** Null for a record with no timestamp, so nothing renders an epoch nobody meant. */
fun clockTime(millis: Long): String? = if (millis <= 0) null else CLOCK.format(Instant.ofEpochMilli(millis))
