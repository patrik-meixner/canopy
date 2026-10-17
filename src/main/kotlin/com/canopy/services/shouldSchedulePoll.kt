package com.canopy.services

/**
 * Whether registering or finishing a tick has to arm the status poll.
 *
 * Arming it only for the first monitored session leaves the loop dead for every session that joins
 * while it is stopped - the count passes through one on the way back up at most once - and a tab
 * whose status is never read never learns which session the agent went on to create.
 */
fun shouldSchedulePoll(isLoopRunning: Boolean, monitoredCount: Int): Boolean =
    !isLoopRunning && monitoredCount > 0
