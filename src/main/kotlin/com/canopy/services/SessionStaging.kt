package com.canopy.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/**
 * Set while the stage is being rearranged.
 *
 * Closing a shell's tab ends it, because a shell has no transcript and nothing to resume: hiding
 * one would leave a process nobody can ever see again. Taking its session off the stage closes the
 * same tab and must not, so the two have to be told apart, and only one of them is the user's.
 */
@Service(Service.Level.PROJECT)
class SessionStaging {

    @Volatile
    private var rearranging = false

    fun <T> rearranging(block: () -> T): T {
        rearranging = true
        try {
            return block()
        } finally {
            rearranging = false
        }
    }

    companion object {
        fun isRearranging(project: Project): Boolean =
            project.getService(SessionStaging::class.java).rearranging

        fun getInstance(project: Project): SessionStaging = project.getService(SessionStaging::class.java)
    }
}
