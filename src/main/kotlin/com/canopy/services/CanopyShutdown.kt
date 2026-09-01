package com.canopy.services

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.components.Service

/**
 * The IDE closes every editor on its way out, so a listener that follows closes would forget every
 * terminal at exactly the moment the list needs to survive. This says the closes are the shutdown's
 * and not the user's; it has to be set before any of them, which is what `appClosing` is for.
 */
@Service
class CanopyShutdown {

    @Volatile
    var isClosing = false

    companion object {
        fun isClosing(): Boolean =
            com.intellij.openapi.application.ApplicationManager.getApplication()
                ?.getService(CanopyShutdown::class.java)?.isClosing ?: true
    }
}

class CanopyShutdownFlag : AppLifecycleListener {

    override fun appClosing() {
        com.intellij.openapi.application.ApplicationManager.getApplication()
            .getService(CanopyShutdown::class.java).isClosing = true
    }
}
