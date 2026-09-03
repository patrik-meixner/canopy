package com.canopy.insight

private val WRITE_TOOLS = setOf("Edit", "Write", "MultiEdit", "NotebookEdit")

fun isWrite(tool: String): Boolean = tool in WRITE_TOOLS
