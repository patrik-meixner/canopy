package com.canopy.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/** How long a project's sidebar is still the platform's to lay out, and how often we may put it back. */
@Service(Service.Level.PROJECT)
class SidebarRestore {

    @Volatile private var settlesAt = 0L
    @Volatile private var attemptsLeft = 0

    val isSettling: Boolean
        get() = attemptsLeft > 0 && System.currentTimeMillis() < settlesAt

    fun begin() {
        settlesAt = System.currentTimeMillis() + SETTLING_MS
        attemptsLeft = MAX_ATTEMPTS
    }

    fun takeAttempt(): Boolean {
        if (attemptsLeft <= 0) return false

        attemptsLeft--

        return true
    }

    companion object {
        fun getInstance(project: Project): SidebarRestore = project.getService(SidebarRestore::class.java)
    }
}

private const val SETTLING_MS = 20_000L
private const val MAX_ATTEMPTS = 4
