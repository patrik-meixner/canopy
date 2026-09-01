package com.canopy.insight

private val WRITE_TOOLS = setOf("Edit", "Write", "MultiEdit", "NotebookEdit")

/** A tool call that changed a file, as opposed to one that only looked at one. */
fun isWrite(tool: String): Boolean = tool in WRITE_TOOLS
