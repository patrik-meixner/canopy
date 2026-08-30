package com.canopy.util

import java.nio.file.Path

fun isSamePath(left: String, right: String): Boolean =
    Path.of(left).normalize().toAbsolutePath() == Path.of(right).normalize().toAbsolutePath()
