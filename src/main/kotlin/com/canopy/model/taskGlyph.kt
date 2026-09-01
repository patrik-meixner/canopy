package com.canopy.model

import com.canopy.toolwindow.spinnerFrame

/** Where a task is, in the vocabulary a session's own state is drawn in. */
fun taskGlyph(isRunning: Boolean, isDone: Boolean, nowMillis: Long): String = when {
    isRunning -> spinnerFrame(nowMillis)
    isDone -> "●"
    else -> "○"
}
