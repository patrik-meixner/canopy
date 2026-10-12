package com.canopy.toolwindow

private val FALLBACK_DEFAULT_BRANCHES = listOf("origin/main", "origin/master", "main", "master")

fun defaultBranchOf(originHead: String?, exists: (String) -> Boolean): String? =
    originHead?.takeIf { it.isNotBlank() } ?: FALLBACK_DEFAULT_BRANCHES.firstOrNull(exists)
