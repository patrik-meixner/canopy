package com.canopy.services

data class RememberedTerminal(
    @JvmField var ownerSessionId: String = "",
    @JvmField var name: String = ""
)
