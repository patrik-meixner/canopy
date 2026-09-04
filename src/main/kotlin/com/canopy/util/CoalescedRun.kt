package com.canopy.util

import java.util.concurrent.atomic.AtomicBoolean

/**
 * One pending run per burst, and never a cancelled one: a debounce that reschedules on every
 * request starves under a stream faster than its own delay, which is what a working agent writes.
 */
class CoalescedRun {

    private val pending = AtomicBoolean(false)

    fun claim(): Boolean = pending.compareAndSet(false, true)

    fun done() = pending.set(false)
}
