package com.canopy.toolwindow

fun pathSignature(paths: Sequence<String>): Long {
    var hash = SEED

    for (path in paths) {
        hash = hash * PRIME + path.hashCode()
    }

    return hash
}

private const val SEED = 1_125_899_906_842_597L
private const val PRIME = 31L
