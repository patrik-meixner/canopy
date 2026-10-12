package com.canopy.toolwindow

fun rootsToSweep(
    current: Map<String, RootFingerprint>,
    swept: Map<String, RootFingerprint>,
    isForced: Boolean
): Set<String> =
    if (isForced) current.keys else current.filter { (root, fingerprint) -> swept[root] != fingerprint }.keys
