package com.canopy.model

import com.canopy.toolwindow.spinnerFrame

fun taskGlyph(isRunning: Boolean, isDone: Boolean, nowMillis: Long): String = when {
    isRunning -> spinnerFrame(nowMillis)
    isDone -> "●"
    else -> "○"
}
