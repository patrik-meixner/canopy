package com.canopy.util

import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

object CanopyExecutor {

    private val log = Logger.getInstance(CanopyExecutor::class.java)
    private val counter = AtomicLong(0)
    private const val DEFAULT_MAX = 16

    private val factory = ThreadFactory { r ->
        Thread(r, "Canopy-${counter.incrementAndGet()}").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY
            uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { t, e ->
                log.warn("Uncaught in ${t.name}", e)
            }
        }
    }

    private val executor = ThreadPoolExecutor(
        DEFAULT_MAX, DEFAULT_MAX,
        60L, TimeUnit.SECONDS,
        LinkedBlockingQueue(),
        factory
    ).apply { allowCoreThreadTimeOut(true) }

    @Volatile private var lastConfiguredMax = DEFAULT_MAX
    @Volatile private var lastSaturationWarnNanos = 0L
    private val resizeLock = Any()

    private fun configuredMax(): Int = try {
        com.canopy.settings.CanopySettings.getInstance()
            .state.backgroundPoolMaxThreads
            .coerceIn(2, 64)
    } catch (_: Throwable) {
        DEFAULT_MAX
    }

    private fun ensurePoolSize(target: Int) {
        if (target == lastConfiguredMax) return
        synchronized(resizeLock) {
            if (target == lastConfiguredMax) return
            // When growing, set core first; when shrinking, set max first.
            // Otherwise core>max or max<core can transiently violate the invariant.
            if (target > lastConfiguredMax) {
                executor.corePoolSize = target
                executor.maximumPoolSize = target
            } else {
                executor.maximumPoolSize = target
                executor.corePoolSize = target
            }
            log.info("Canopy: background pool resized $lastConfiguredMax -> $target")
            lastConfiguredMax = target
        }
    }

    fun submit(task: Runnable) {
        val target = configuredMax()
        ensurePoolSize(target)

        val active = executor.activeCount
        if (active >= target) {
            val now = System.nanoTime()
            if (now - lastSaturationWarnNanos > 60_000_000_000L) {
                lastSaturationWarnNanos = now
                log.warn(
                    "Canopy: background pool saturated — $active/$target threads active, " +
                        "${executor.queue.size} queued. Raise 'Background pool max threads' " +
                        "in Settings > Tools > Canopy if this persists."
                )
            }
        }

        executor.execute(task)
    }
}
