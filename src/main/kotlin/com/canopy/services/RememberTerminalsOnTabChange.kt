package com.canopy.services

import com.canopy.editor.ClaudeSessionVirtualFile
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * A close is followed only while the IDE is staying up. Shutdown closes every tab before anything
 * gets to save, so following those would write an empty list over the one worth keeping.
 */
class RememberTerminalsOnTabChange(private val project: Project) : FileEditorManagerListener, CanopyTabsListener {

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        if (file !is ClaudeSessionVirtualFile) return

        rememberOpenTerminals(project, "fileOpened")
    }

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        if (file !is ClaudeSessionVirtualFile) return
        if (CanopyShutdown.isClosing()) return

        rememberOpenTerminals(project, "fileClosed")
    }

    override fun selectionChanged(event: com.intellij.openapi.fileEditor.FileEditorManagerEvent) {
        val session = event.newFile as? ClaudeSessionVirtualFile ?: return
        if (session.isShellSession) return

        ActiveSessionTracker.getInstance(project).lastSessionKey = session.sessionKey
    }

    override fun tabsChanged() = rememberOpenTerminals(project, "tabsChanged")
}
