package com.canopy.services

import com.canopy.editor.ClaudeSessionVirtualFile
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project

fun rememberOpenTerminals(project: Project) {
    val tabs = FileEditorManager.getInstance(project).openFiles
        .filterIsInstance<ClaudeSessionVirtualFile>()
        .map { OpenTab(it.sessionKey, it.sessionId, it.isShellSession, it.ownerSessionKey, it.baseName) }

    OpenSessionsPersistence.getInstance(project).rememberTerminals(rememberedTerminals(tabs))
}
