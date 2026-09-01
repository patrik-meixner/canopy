package com.canopy.toolwindow

private val JUNK = setOf(".DS_Store", "Thumbs.db", "desktop.ini", ".Spotlight-V100", ".Trashes", "._.DS_Store")

/**
 * A file the operating system wrote, not the session.
 *
 * A repository that does not ignore these buries the untracked section under one per directory,
 * and none of them is ever a thing to review, commit or read.
 */
fun isOperatingSystemJunk(path: String): Boolean = path.substringAfterLast('/') in JUNK
