package com.canopy.toolwindow

import com.canopy.settings.ClaudeProfile
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup

fun profileActionGroup(
    title: String,
    profiles: List<ClaudeProfile>,
    isEnabled: () -> Boolean,
    onPick: (String) -> Unit
): DefaultActionGroup = DefaultActionGroup(title, true).apply {
    isPopup = true
    profiles.forEach { profile ->
        add(object : AnAction(profile.name, "Run this session on the ${profile.name} account", null) {
            override fun actionPerformed(e: AnActionEvent) = onPick(profile.name)
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = isEnabled()
            }
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
        })
    }
    templatePresentation.isEnabledAndVisible = profiles.size > 1
}
