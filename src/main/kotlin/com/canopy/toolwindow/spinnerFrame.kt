package com.canopy.toolwindow

private val FRAMES = listOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")

const val SPINNER_FRAME_MS = 110L

fun spinnerFrame(millis: Long): String = FRAMES[((millis / SPINNER_FRAME_MS) % FRAMES.size).toInt()]

fun isSpinnerFrame(glyph: String): Boolean = glyph in FRAMES
