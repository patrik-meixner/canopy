package com.canopy.toolwindow

/**
 * A number naming a set of paths, so an unchanged result can be recognised without building a
 * string out of it.
 *
 * The pushed section alone runs to thousands of files, and the comparison happens on every refresh.
 */
fun pathSignature(paths: Sequence<String>): Long {
    var hash = SEED

    for (path in paths) {
        hash = hash * PRIME + path.hashCode()
    }

    return hash
}

private const val SEED = 1_125_899_906_842_597L
private const val PRIME = 31L
