package com.canopy.editor

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project

/**
 * The working directory comes from the session rather than the project: a session running in a
 * worktree wants a shell in that worktree, not in the checkout it was launched from.
 */
fun openTerminalFor(project: Project, owner: ClaudeSessionVirtualFile?) {
    val file = ClaudeSessionVirtualFile(
        name = "Terminal",
        isShellSession = true,
        ownerSessionKey = owner?.sessionKey
    ).apply {
        workingDir = owner?.workingDir ?: project.basePath
    }

    FileEditorManager.getInstance(project).openFile(file, true)
}

/**
 * A shell opened while another shell is on screen becomes its sibling rather than its child: a
 * shell owned by a shell has no session row to sit under and would vanish from the list.
 */
fun ownerOnScreen(project: Project): ClaudeSessionVirtualFile? {
    val selected = FileEditorManager.getInstance(project).selectedFiles
        .filterIsInstance<ClaudeSessionVirtualFile>()
        .firstOrNull() ?: return null
    if (!selected.isShellSession) return selected

    val ownerKey = selected.ownerSessionKey ?: return null

    return FileEditorManager.getInstance(project).openFiles
        .filterIsInstance<ClaudeSessionVirtualFile>()
        .firstOrNull { it.sessionKey == ownerKey }
}
