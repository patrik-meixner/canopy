package com.canopy.toolwindow

/** A branch name cannot hold spaces or most punctuation, and what it is made from is free text. */
internal fun slug(label: String): String =
    label.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40).ifEmpty { "session" }
