package com.canopy.services

data class OpenTab(
    val sessionKey: String,
    val sessionId: String?,
    val isShell: Boolean,
    val ownerKey: String?,
    val name: String
)

fun rememberedTerminals(tabs: List<OpenTab>): List<RememberedTerminal> {
    val sessionIdByKey = tabs.filterNot { it.isShell }
        .mapNotNull { tab -> tab.sessionId?.let { tab.sessionKey to it } }
        .toMap()

    return tabs.filter { it.isShell }.mapNotNull { shell ->
        val ownerId = sessionIdByKey[shell.ownerKey] ?: return@mapNotNull null

        RememberedTerminal(ownerId, shell.name)
    }
}
