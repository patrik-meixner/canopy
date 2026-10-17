package com.canopy.util

/**
 * Builds a git command line that leaves the index alone.
 *
 * `git status` refreshes the index as a side effect, and taking `.git/index.lock` to do it makes a
 * commit or a rebase running next to it fail. Canopy reads repository state on a timer, so every
 * call it makes passes the flag that skips those optional writes; the locks a write genuinely needs
 * are unaffected.
 */
fun gitCommand(workingDir: String, vararg args: String): Array<String> =
    arrayOf("git", "--no-optional-locks", "-C", workingDir, *args)
