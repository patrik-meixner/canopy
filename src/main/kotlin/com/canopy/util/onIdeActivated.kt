package com.canopy.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.wm.IdeFrame

fun onIdeActivated(parent: Disposable, action: () -> Unit) {
    ApplicationManager.getApplication().messageBus.connect(parent).subscribe(
        ApplicationActivationListener.TOPIC,
        object : ApplicationActivationListener {
            override fun applicationActivated(ideFrame: IdeFrame) = action()
        }
    )
}
