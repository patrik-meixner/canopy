package com.canopy.insight

import com.canopy.toolwindow.SessionMessage

fun messageCardKey(message: SessionMessage): String = "${message.ordinal}:${message.text.length}"
