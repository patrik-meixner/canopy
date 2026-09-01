package com.canopy.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/**
 * The session tab that was on screen most recently, which is not the same as the one selected now:
 * clicking into a source file deselects every Canopy tab, and a terminal opened at that moment
 * still belongs to the session the user was working in.
 */
@Service(Service.Level.PROJECT)
class ActiveSessionTracker {

    @Volatile
    var lastSessionKey: String? = null

    companion object {
        fun getInstance(project: Project): ActiveSessionTracker =
            project.getService(ActiveSessionTracker::class.java)
    }
}
