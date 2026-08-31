package com.canopy.toolwindow

import com.canopy.editor.ClaudeSessionVirtualFile
import com.canopy.model.SessionDisplay
import com.canopy.util.ClaudePathEncoder
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project

fun openClaudeSession(project: Project, session: SessionDisplay) {
    val manager = FileEditorManager.getInstance(project)
    val existing = manager.openFiles
        .filterIsInstance<ClaudeSessionVirtualFile>()
        .firstOrNull { it.sessionId == session.sessionId }

    if (existing != null) {
        manager.openFile(existing, true)
        return
    }

    val file = ClaudeSessionVirtualFile(session.tabTitle, session.sessionId).apply {
        if (session.worktreeName != null && project.basePath != null) {
            workingDir = ClaudePathEncoder.worktreeAbsolutePath(project.basePath!!, session.worktreeName)
            isWorktreeSession = true
        }
    }
    manager.openFile(file, true)
}
