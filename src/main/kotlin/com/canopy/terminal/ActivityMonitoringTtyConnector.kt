package com.canopy.terminal

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.pty4j.PtyProcess
import java.nio.charset.Charset
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Fires [onActiveChanged] as the far end starts and stops writing, and [onUnresponsive] when a
 * keystroke has gone [echoTimeoutMs] without anything coming back.
 *
 * Output within [echoWindowMs] of a write is the PTY echoing the keystroke, not the agent thinking,
 * so it does not count as activity. Callbacks are always invoked on the EDT.
 */
class ActivityMonitoringTtyConnector(
    process: PtyProcess,
    charset: Charset,
    private val idleTimeoutMs: Long = 1200,
    private val echoWindowMs: Long = 500,
    private val echoTimeoutMs: Long = 3000,
    private val onActiveChanged: (active: Boolean) -> Unit,
    private val onUserInput: (() -> Unit)? = null,
    private val onUnresponsive: (() -> Unit)? = null,
    private val onResponsive: (() -> Unit)? = null
) : FilteringPtyConnector(process, charset) {

    @Volatile private var active = false
    @Volatile private var lastWriteMs: Long = 0
    @Volatile private var lastReadMs: Long = 0
    @Volatile private var unresponsiveNotified = false
    private var idleFuture: ScheduledFuture<*>? = null
    private var echoCheckFuture: ScheduledFuture<*>? = null

    override fun read(buf: CharArray, offset: Int, length: Int): Int {
        val count = super.read(buf, offset, length)
        if (count <= 0) return count

        lastReadMs = System.currentTimeMillis()
        if (unresponsiveNotified) {
            unresponsiveNotified = false
            onResponsive?.let { cb -> ApplicationManager.getApplication().invokeLater(cb) }
        }
        if (lastReadMs - lastWriteMs > echoWindowMs) markActive()

        return count
    }

    private fun markActive() {
        if (!active) {
            active = true
            ApplicationManager.getApplication().invokeLater { onActiveChanged(true) }
        }
        idleFuture?.cancel(false)
        idleFuture = AppExecutorUtil.getAppScheduledExecutorService().schedule({
            active = false
            ApplicationManager.getApplication().invokeLater { onActiveChanged(false) }
        }, idleTimeoutMs, TimeUnit.MILLISECONDS)
    }

    override fun write(bytes: ByteArray) {
        super.write(bytes)
        scheduleEchoCheck()
        onUserInput?.let { cb -> ApplicationManager.getApplication().invokeLater(cb) }
    }

    override fun write(string: String) {
        super.write(string)
        scheduleEchoCheck()
        onUserInput?.let { cb -> ApplicationManager.getApplication().invokeLater(cb) }
    }

    private fun scheduleEchoCheck() {
        lastWriteMs = System.currentTimeMillis()
        unresponsiveNotified = false
        echoCheckFuture?.cancel(false)
        echoCheckFuture = AppExecutorUtil.getAppScheduledExecutorService().schedule({
            if (!unresponsiveNotified && lastReadMs < lastWriteMs) {
                unresponsiveNotified = true
                onUnresponsive?.let { cb -> ApplicationManager.getApplication().invokeLater(cb) }
            }
        }, echoTimeoutMs, TimeUnit.MILLISECONDS)
    }

    override fun close() {
        idleFuture?.cancel(false)
        echoCheckFuture?.cancel(false)
        super.close()
    }
}
