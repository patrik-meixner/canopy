package com.canopy.toolwindow

fun ownDirectoriesOf(launchDirectory: String?, written: Set<String>, isDirectory: (String) -> Boolean): List<String> =
    listOfNotNull(launchDirectory) + written.filter(isDirectory)
