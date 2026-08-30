package com.canopy.insight

import com.canopy.editor.ClaudeSessionVirtualFile
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import java.awt.BorderLayout
import java.awt.event.HierarchyEvent
import javax.swing.JPanel

/**
 * Shared plumbing for the tabs that read one session: follow the selected session tab, subscribe
 * to the parsed view, and tick only while actually on screen.
 */
abstract class InsightTabPanel(protected val project: Project, parent: Disposable) : JPanel(BorderLayout()), Disposable {

    private val service = SessionInsightService.getInstance(project)
    private var watching = false

    init {
        Disposer.register(parent, this)

        service.addListener(this) { insight ->
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed || Disposer.isDisposed(this)) return@invokeLater

                render(insight)
            }
        }

        addHierarchyListener { event ->
            if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() == 0L) return@addHierarchyListener

            if (isShowing) startWatching() else stopWatching()
        }

        project.messageBus.connect(this).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    val session = event.newFile as? ClaudeSessionVirtualFile ?: return

                    service.bind(session.sessionId, session.workingDir)
                }
            }
        )

        bindToSelection()
    }

    protected abstract fun render(insight: SessionInsight)

    protected fun selectedSession(): ClaudeSessionVirtualFile? =
        FileEditorManager.getInstance(project).selectedFiles.firstOrNull() as? ClaudeSessionVirtualFile

    private fun bindToSelection() {
        val session = selectedSession() ?: return

        service.bind(session.sessionId, session.workingDir)
    }

    private fun startWatching() {
        if (watching) return
        watching = true
        bindToSelection()
        service.watch()
    }

    private fun stopWatching() {
        if (!watching) return
        watching = false
        service.unwatch()
    }

    override fun dispose() = stopWatching()
}
