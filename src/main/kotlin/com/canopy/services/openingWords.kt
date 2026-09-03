package com.canopy.services

fun openingWords(prompt: String): String = if (prompt.length <= OPENING_WORDS) prompt else prompt.take(OPENING_WORDS)

private const val OPENING_WORDS = 200
