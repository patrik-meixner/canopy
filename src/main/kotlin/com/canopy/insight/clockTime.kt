package com.canopy.insight

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

fun clockTime(millis: Long): String? = if (millis <= 0) null else CLOCK.format(Instant.ofEpochMilli(millis))
