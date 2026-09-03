package com.canopy.toolwindow

/**
 * Where the session's shell wrote without naming a path: the directory it was launched in, and every
 * directory a writing command changed into or named. A repository it reached into for one file is
 * not a directory of its own - that file is claimed by name, and nothing else in there is.
 */
fun ownDirectoriesOf(launchDirectory: String?, written: Set<String>, isDirectory: (String) -> Boolean): List<String> =
    listOfNotNull(launchDirectory) + written.filter(isDirectory)
