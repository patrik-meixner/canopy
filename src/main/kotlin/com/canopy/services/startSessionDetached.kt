package com.canopy.services

import com.canopy.editor.ClaudeSessionVirtualFile
import com.canopy.model.SessionDisplay
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import javax.swing.JPanel

private const val OFFSCREEN_WIDTH = 1200
private const val OFFSCREEN_HEIGHT = 800

/**
 * Resumes a session with no tab to show it.
 *
 * A restart brings every agent that was working back, but puts one of them on the stage: the rest
 * are running, listed and answerable, and cost nothing on screen until they are clicked. The
 * terminal is parked in a panel of its own size because JediTerm reads its width to decide how
 * wide to start, and a widget in nothing at all starts narrow enough to wrap the whole transcript.
 */
fun startSessionDetached(project: Project, session: SessionDisplay, workingDirectory: String?) {
    val runtimes = SessionRuntimeService.getInstance(project)
    if (runtimes.existing(session.sessionId) != null) return

    val file = ClaudeSessionVirtualFile(session.tabTitle, session.sessionId).apply {
        workingDir = workingDirectory
        isWorktreeSession = session.worktreeName != null
    }
    val statusService = ClaudeStatusService.getInstance(project)
    val statusFile = statusService.createStatusFilePath(session.sessionId)
    val notifyFile = statusService.createNotifyFilePath(session.sessionId)

    val runtime = runtimes.create(session.sessionId, file) { parent, relay ->
        ClaudeTerminalService.getInstance(project).createResumeWidget(
            session.sessionId,
            parent,
            workingDir = workingDirectory,
            statusFile = statusFile,
            notifyFile = notifyFile,
            onActiveChanged = relay::onActiveChanged,
            onUserInput = relay::onUserInput,
            onUnresponsive = relay::onUnresponsive,
            onResponsive = relay::onResponsive
        )
    }

    park(runtime)
    statusService.startMonitoring(session.sessionId, statusFile, notifyFile)
    Disposer.register(runtime.disposable, { statusService.stopMonitoring(session.sessionId) })
}

private fun park(runtime: SessionRuntime) {
    JPanel(java.awt.BorderLayout()).apply {
        setSize(OFFSCREEN_WIDTH, OFFSCREEN_HEIGHT)
        add(runtime.session.widget.component, java.awt.BorderLayout.CENTER)
        doLayout()
    }
}
