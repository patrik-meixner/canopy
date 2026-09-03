package com.canopy.model

data class RepoScope(
    val label: String,
    val root: String,
    val gitCommonDir: String,
    val isSubmodule: Boolean
)
