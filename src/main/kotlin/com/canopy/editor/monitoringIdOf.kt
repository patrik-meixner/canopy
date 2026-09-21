package com.canopy.editor

/**
 * What the agent's status and notify files are named after. It has to be the tab's own identity:
 * a freshly generated one gave a reopened tab a second name, and the files the running agent was
 * already writing were left with nobody reading them.
 */
fun monitoringIdOf(sessionId: String?, sessionKey: String): String = sessionId ?: sessionKey
