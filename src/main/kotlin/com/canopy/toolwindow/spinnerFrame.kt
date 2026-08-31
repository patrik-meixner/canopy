package com.canopy.toolwindow

private val FRAMES = listOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")

const val SPINNER_FRAME_MS = 110L

/**
 * Which frame of the spinner belongs to a moment in time.
 *
 * Driving it from the clock rather than a counter means every row is on the same frame however
 * often each one happens to be repainted.
 */
fun spinnerFrame(millis: Long): String = FRAMES[((millis / SPINNER_FRAME_MS) % FRAMES.size).toInt()]
