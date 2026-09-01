package com.canopy.services

import com.canopy.editor.ClaudeSessionVirtualFile
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener

/**
 * Snapshotting at close rather than on every event is also what makes a rename stick: the name
 * written down is whatever the tab is called at the moment the IDE goes away.
 */
class RememberTerminalsOnClose : ProjectCloseListener {

    override fun projectClosing(project: Project) {
        val tabs = FileEditorManager.getInstance(project).openFiles
            .filterIsInstance<ClaudeSessionVirtualFile>()
            .map { OpenTab(it.sessionKey, it.sessionId, it.isShellSession, it.ownerSessionKey, it.baseName) }

        OpenSessionsPersistence.getInstance(project).rememberTerminals(rememberedTerminals(tabs))
    }
}
