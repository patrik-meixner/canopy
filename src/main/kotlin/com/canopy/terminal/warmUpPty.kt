package com.canopy.terminal

import com.intellij.openapi.diagnostic.Logger
import com.pty4j.PtyProcess

/**
 * Loads pty4j's native library on the calling thread.
 *
 * The first PtyProcess of a run dlopens a freshly extracted dylib, which macOS stops to verify.
 * Doing that where it happens by default - inside the editor opening a session - blocks the EDT
 * for as long as the check takes, so the call is made off it before any session is opened.
 */
fun warmUpPty(): Boolean {
    return try {
        Class.forName("com.pty4j.unix.PtyHelpers", true, PtyProcess::class.java.classLoader)
        true
    } catch (e: Throwable) {
        LOG.info("Canopy: pty warm-up did not load the native library", e)
        false
    }
}

private val LOG = Logger.getInstance("com.canopy.terminal.WarmUpPty")
