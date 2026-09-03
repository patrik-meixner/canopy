package com.canopy.util

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project

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
