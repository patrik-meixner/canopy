package com.canopy.services

import com.canopy.editor.ClaudeSessionVirtualFile
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Opening and renaming are followed, closing is not: the IDE closes every tab on its way out, and
 * a snapshot taken then would save an empty list over the one worth keeping.
 */
class RememberTerminalsOnTabChange(private val project: Project) : FileEditorManagerListener, CanopyTabsListener {

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        if (file !is ClaudeSessionVirtualFile) return

        rememberOpenTerminals(project, "fileOpened")
    }

    override fun selectionChanged(event: com.intellij.openapi.fileEditor.FileEditorManagerEvent) {
        val session = event.newFile as? ClaudeSessionVirtualFile ?: return
        if (session.isShellSession) return

        ActiveSessionTracker.getInstance(project).lastSessionKey = session.sessionKey
    }

    override fun tabsChanged() = rememberOpenTerminals(project, "tabsChanged")
}
