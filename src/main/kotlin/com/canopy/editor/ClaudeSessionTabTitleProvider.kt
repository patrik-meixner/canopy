package com.canopy.editor

import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class ClaudeSessionTabTitleProvider : EditorTabTitleProvider {
    override fun getEditorTabTitle(project: Project, file: VirtualFile): String? {
        val session = file as? ClaudeSessionVirtualFile ?: return null

        return if (session.isShellSession) terminalLabel(project, session) else session.computeTabTitle()
    }

    override fun getEditorTabTooltipText(project: Project, virtualFile: VirtualFile): String? {
        val file = virtualFile as? ClaudeSessionVirtualFile ?: return null
        val parts = mutableListOf<String>()
        file.sessionId?.let { parts.add("Session: $it") }
        if (file.isWorktreeSession) file.workingDir?.let { parts.add("Worktree: $it") }
        if (parts.isEmpty()) parts.add("Session not started yet")
        return parts.joinToString(" — ")
    }
}
