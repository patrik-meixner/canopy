package com.canopy.toolwindow

data class GitDiffEntry(
    val status: Char,
    val oldPath: String,
    val newPath: String,
    val isSubmodule: Boolean,
    val modeChanged: Boolean,
    val hasContentChange: Boolean
)
