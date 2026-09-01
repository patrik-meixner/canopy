package com.canopy.services

/** A tab as the persistence layer sees it, with no platform types in the way. */
data class OpenTab(
    val sessionKey: String,
    val sessionId: String?,
    val isShell: Boolean,
    val ownerKey: String?,
    val name: String
)

/**
 * The shells worth reopening next time, named by the session they belong to.
 *
 * A session key is a nanosecond stamp for the life of one IDE run, so what survives a restart is
 * the owner's session id. A shell whose session never got one - it was never prompted, so it has
 * no transcript and will not be restored either - has nothing to hang off and is dropped.
 */
fun rememberedTerminals(tabs: List<OpenTab>): List<RememberedTerminal> {
    val sessionIdByKey = tabs.filterNot { it.isShell }
        .mapNotNull { tab -> tab.sessionId?.let { tab.sessionKey to it } }
        .toMap()

    return tabs.filter { it.isShell }.mapNotNull { shell ->
        val ownerId = sessionIdByKey[shell.ownerKey] ?: return@mapNotNull null

        RememberedTerminal(ownerId, shell.name)
    }
}
