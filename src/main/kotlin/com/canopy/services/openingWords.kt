package com.canopy.services

/**
 * As much of a first prompt as anything ever reads.
 *
 * The list shows sixty characters of it and a tab forty; the search box matches against it, and
 * full text is what the transcript search is for. Kept whole, one pasted prompt reached half a
 * megabyte and was written into the project's settings on every save.
 */
fun openingWords(prompt: String): String = if (prompt.length <= OPENING_WORDS) prompt else prompt.take(OPENING_WORDS)

private const val OPENING_WORDS = 200
