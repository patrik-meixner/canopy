package com.canopy.editor

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project

fun openTerminalFor(project: Project, owner: ClaudeSessionVirtualFile?, name: String = "Terminal") {
    val file = ClaudeSessionVirtualFile(
        name = name,
        isShellSession = true,
        ownerSessionKey = owner?.sessionKey
    ).apply {
        workingDir = owner?.workingDir ?: project.basePath
    }

    FileEditorManager.getInstance(project).openFile(file, true)
}

fun ownerOnScreen(project: Project): ClaudeSessionVirtualFile? {
    val sessions = FileEditorManager.getInstance(project).openFiles
        .filterIsInstance<ClaudeSessionVirtualFile>()
    val selected = FileEditorManager.getInstance(project).selectedFiles
        .filterIsInstance<ClaudeSessionVirtualFile>()
        .firstOrNull()

    val ownerKey = when {
        selected == null -> com.canopy.services.ActiveSessionTracker.getInstance(project).lastSessionKey
        selected.isShellSession -> selected.ownerSessionKey
        else -> selected.sessionKey
    } ?: return null

    return sessions.firstOrNull { !it.isShellSession && it.sessionKey == ownerKey }
}
