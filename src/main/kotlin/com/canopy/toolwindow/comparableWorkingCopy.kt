package com.canopy.toolwindow

/**
 * Git stores what its clean filter produces, so a repository that normalises line endings holds a
 * blob with none while the file on disk has them. Comparing the two as they are marks every line
 * changed; git itself reports only the lines that really differ.
 */
fun comparableWorkingCopy(committed: String?, working: String): String {
    if (committed == null || "\r\n" in committed) return working

    return working.replace("\r\n", "\n")
}
