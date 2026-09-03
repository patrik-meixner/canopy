package com.canopy.toolwindow

private val JUNK = setOf(".DS_Store", "Thumbs.db", "desktop.ini", ".Spotlight-V100", ".Trashes", "._.DS_Store")

fun isOperatingSystemJunk(path: String): Boolean = path.substringAfterLast('/') in JUNK
