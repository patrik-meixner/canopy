package com.canopy.editor

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project

/**
 * The working directory comes from the session rather than the project: a session running in a
 * worktree wants a shell in that worktree, not in the checkout it was launched from.
 */
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

/**
 * A shell opened while another shell is on screen becomes its sibling rather than its child: a
 * shell owned by a shell has no session row to sit under and would vanish from the list.
 *
 * Falling back to the last session on screen is what makes the + on the tab strip do the obvious
 * thing after a detour into a source file, which deselects every Canopy tab.
 */
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
