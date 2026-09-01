package com.canopy.util

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project

/**
 * Work the IDE shows while it happens, rather than work that happens silently.
 *
 * Committing four repositories and pushing them is seconds of git with nothing on screen to say so:
 * the tree simply changed a while later, or did not. A background task puts it in the same progress
 * bar every other VCS operation uses.
 *
 * Not cancellable: git is already running by the time a cancel could arrive, and half a commit
 * across four repositories is worse than waiting.
 */
fun <T : Any> inBackground(project: Project, title: String, work: (ProgressIndicator) -> T, onDone: (T) -> Unit) {
    ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, false) {
        private var outcome: T? = null

        override fun run(indicator: ProgressIndicator) {
            outcome = work(indicator)
        }

        override fun onSuccess() {
            outcome?.let(onDone)
        }
    })
}
