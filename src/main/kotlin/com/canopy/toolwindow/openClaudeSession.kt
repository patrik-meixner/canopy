package com.canopy.toolwindow

import com.canopy.editor.ClaudeSessionVirtualFile
import com.canopy.model.SessionDisplay
import com.canopy.util.ClaudePathEncoder
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project

fun openClaudeSession(project: Project, session: SessionDisplay) {
    val manager = FileEditorManager.getInstance(project)
    // A session running without a tab already has a file, and its runtime is filed under it: a
    // second file would open a second agent beside the one that is already working.
    val existing = com.canopy.services.SessionRuntimeService.getInstance(project)
        .existing(session.sessionId)?.file
        ?: manager.openFiles
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
