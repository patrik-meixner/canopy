package com.canopy.util

import java.util.concurrent.atomic.AtomicBoolean

class DeferredRefresh(
    private val isSeen: () -> Boolean,
    private val catchUpForced: Boolean = false,
    private val run: (isForced: Boolean) -> Unit
) {

    private val stale = AtomicBoolean(false)

    fun request(isForced: Boolean = false) {
        if (!isSeen()) return stale.set(true)

        stale.set(false)
        run(isForced)
    }

    fun catchUp() {
        if (!isSeen() || !stale.compareAndSet(true, false)) return

        run(catchUpForced)
    }
}
