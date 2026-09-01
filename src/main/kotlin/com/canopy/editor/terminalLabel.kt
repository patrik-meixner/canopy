package com.canopy.editor

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project

/** Whose shell this is, said out loud only while there is more than one session it could be. */
fun terminalLabel(project: Project, shell: ClaudeSessionVirtualFile): String {
    val sessions = FileEditorManager.getInstance(project).openFiles
        .filterIsInstance<ClaudeSessionVirtualFile>()
        .filterNot { it.isShellSession }
    if (sessions.size < 2) return shell.baseName

    val owner = sessions.firstOrNull { it.sessionKey == shell.ownerSessionKey } ?: return shell.baseName

    return "[${owner.baseName}] ${shell.baseName}"
}
