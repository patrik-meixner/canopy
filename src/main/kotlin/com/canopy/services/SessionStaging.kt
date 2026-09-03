package com.canopy.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

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
