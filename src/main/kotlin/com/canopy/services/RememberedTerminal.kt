package com.canopy.services

/** Written to the workspace file, so it needs the no-arg constructor the defaults give it. */
data class RememberedTerminal(
    @JvmField var ownerSessionId: String = "",
    @JvmField var name: String = ""
)
