package com.canopy.services

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.components.Service

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
