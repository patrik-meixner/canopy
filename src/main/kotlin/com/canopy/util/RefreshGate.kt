package com.canopy.util

import java.util.concurrent.atomic.AtomicBoolean

class RefreshGate(private val floorMillis: Long) {

    private val running = AtomicBoolean(false)

    @Volatile private var lastStartedAt: Long? = null

    fun tryStart(isForced: Boolean, now: Long = System.currentTimeMillis()): Boolean {
        val last = lastStartedAt
        if (!isForced && last != null && now - last < floorMillis) return false
        if (!running.compareAndSet(false, true)) return false

        lastStartedAt = now

        return true
    }

    fun finish() = running.set(false)
}
