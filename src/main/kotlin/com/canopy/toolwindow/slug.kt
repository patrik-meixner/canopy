package com.canopy.toolwindow

internal fun slug(label: String): String =
    label.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40).ifEmpty { "session" }
