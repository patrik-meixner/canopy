package com.canopy.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class ActiveSessionTracker {

    @Volatile
    var lastSessionKey: String? = null

    companion object {
        fun getInstance(project: Project): ActiveSessionTracker =
            project.getService(ActiveSessionTracker::class.java)
    }
}
