package com.canopy.toolwindow

import com.canopy.editor.ClaudeSessionVirtualFile
import com.canopy.model.SessionDisplay
import com.canopy.model.SessionStatus
import com.canopy.services.ClaudeSessionService
import com.canopy.util.ClaudePathEncoder
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.ui.content.ContentFactory

class ClaudeSessionsToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val sessionService = ClaudeSessionService.getInstance(project)

        val getStatus = { sessionId: String ->
            when {
                // Plugin ownership wins: a session open in a Canopy tab is never
                // "external", even if a stray/background claude process references it.
                isOpenInPlugin(project, sessionId) -> SessionStatus.OPEN_IN_PLUGIN
                sessionService.isExternallyOpen(sessionId) -> SessionStatus.OPEN_EXTERNALLY
                else -> SessionStatus.AVAILABLE
            }
        }

        val callbacks = SessionCallbacks(
            onSessionSelected = { session -> openClaudeSession(project, session) },
            onNewSession = { name -> openNewSession(project, name) },
            onForkSession = { session -> forkSession(project, session) }
        )

        // Blanking the title would still reserve its slot; this drops the label itself, and the
        // name survives for the stripe tooltip and the View menu.
        toolWindow.component.putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, "true")

        /** Reviewing a session happens in its own window, so opening one brings that window up. */
        val openForReview: (SessionDisplay) -> Unit = { session ->
            callbacks.onSessionSelected(session)
            SessionWindow.activateDetail(project)
        }

        val sessionsPanel = SessionListPanel(
            project, sessionService, getStatus,
            openForReview, callbacks.onNewSession, callbacks.onForkSession
        )
        val sessionsContent = ContentFactory.getInstance().createContent(sessionsPanel.getComponent(), "Sessions", false)
        sessionsContent.icon = IconLoader.getIcon("/icons/canopy.svg", ClaudeSessionsToolWindowFactory::class.java)
        sessionsContent.isCloseable = false
        sessionsContent.setDisposer(sessionsPanel)
        toolWindow.contentManager.addContent(sessionsContent)

        val repoTreePanel = WorktreeTreePanel(
            project,
            toolWindow.disposable,
            onWorktreeSelected = { worktree -> sessionsPanel.filterByWorktree(worktree?.entry?.path?.let(::directoryName)) },
            onWorktreeActivated = { worktree -> offerSessionsForWorktree(project, sessionService, worktree) },
            loadSessions = { sessionService.getSessions() },
            attentionOf = { session ->
                com.canopy.model.sessionAttentionFor(
                    notifyState = com.canopy.services.ClaudeStatusService.getInstance(project).getNotifyState(session.sessionId),
                    isRunning = sessionService.isExternallyOpen(session.sessionId) ||
                        isOpenInPlugin(project, session.sessionId),
                    tail = session.tail,
                    idleForMillis = System.currentTimeMillis() - session.modified.toEpochMilli()
                )
            },
            onSessionActivated = openForReview,
            onNewWorktree = { scope, name -> openNewWorktreeSession(project, name, scope.root) }
        )
        val repoTreeContent = ContentFactory.getInstance().createContent(repoTreePanel, "Workspaces", false)
        repoTreeContent.icon = IconLoader.getIcon("/icons/worktree.svg", ClaudeSessionsToolWindowFactory::class.java)
        repoTreeContent.isCloseable = false
        toolWindow.contentManager.addContent(repoTreeContent)


        sessionService.startWatching()

        val editorListener = object : FileEditorManagerListener {
            override fun selectionChanged(event: FileEditorManagerEvent) {
                sessionsPanel.selectSession((event.newFile as? ClaudeSessionVirtualFile)?.sessionId)
            }

            override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                if (file is ClaudeSessionVirtualFile) {
                    sessionsPanel.refreshStatuses()
                    repoTreePanel.refresh(force = true)
                }
            }
            override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
                if (file is ClaudeSessionVirtualFile) {
                    sessionsPanel.refreshStatuses()
                    repoTreePanel.refresh(force = true)
                }
            }
        }
        project.messageBus.connect(sessionsPanel).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER, editorListener
        )

    }

    private data class SessionCallbacks(
        val onSessionSelected: (SessionDisplay) -> Unit,
        val onNewSession: (String) -> Unit,
        val onForkSession: (SessionDisplay) -> Unit
    )

    private fun isOpenInPlugin(project: Project, sessionId: String): Boolean {
        return FileEditorManager.getInstance(project).openFiles
            .filterIsInstance<ClaudeSessionVirtualFile>()
            .any { it.sessionId == sessionId }
    }


    private fun openNewSession(project: Project, name: String) {
        val file = ClaudeSessionVirtualFile(name.ifBlank { com.canopy.editor.UNNAMED_SESSION_TITLE }).apply {
            requestedName = name.ifBlank { null }
        }
        FileEditorManager.getInstance(project).openFile(file, true)
    }

    /**
     * A worktree usually already has sessions behind it, so activating one offers those first and
     * only falls through to a fresh session when there are none.
     */
    private fun offerSessionsForWorktree(
        project: Project,
        sessionService: ClaudeSessionService,
        worktree: WorktreeTreeNode.Worktree
    ) {
        val name = directoryName(worktree.entry.path)
        val existing = sessionService.getSessions().filter { it.worktreeName == name }
        if (existing.isEmpty()) return openSessionInDirectory(project, name, worktree.entry.path)

        val newSessionLabel = "New session here"
        val choices = existing.map { it.displayName } + newSessionLabel

        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(choices)
            .setTitle("Sessions in $name")
            .setItemChosenCallback { chosen ->
                if (chosen == newSessionLabel) {
                    openSessionInDirectory(project, name, worktree.entry.path)
                } else {
                    existing.firstOrNull { it.displayName == chosen }?.let { openClaudeSession(project, it) }
                }
            }
            .createPopup()
            .showInFocusCenter()
    }

    private fun directoryName(path: String): String =
        java.nio.file.Path.of(path).fileName?.toString() ?: path

    /** Opens a session rooted in an existing worktree directory, rather than creating a new worktree. */
    private fun openSessionInDirectory(project: Project, name: String, directory: String) {
        val file = ClaudeSessionVirtualFile(name).apply { workingDir = directory }
        FileEditorManager.getInstance(project).openFile(file, true)
    }

    private fun openNewWorktreeSession(project: Project, worktreeName: String, repoRoot: String? = null) {
        val file = ClaudeSessionVirtualFile(worktreeName, newWorktreeName = worktreeName).apply {
            // The worktree directory is deterministic from the name (the CLI normalizes
            // '/'→'+', mirrored by WorktreeInspector.normalize), so resolve it up front —
            // the git/worktree toolbars can show the real branch before the first message
            // instead of falling back to the main repo and showing "main". The CLI creates
            // the dir asynchronously after spawn; the toolbars tolerate it not existing yet.
            val owner = repoRoot ?: project.basePath
            if (owner != null) {
                workingDir = ClaudePathEncoder.worktreeAbsolutePath(owner, WorktreeInspector.normalize(worktreeName))
            }
        }
        FileEditorManager.getInstance(project).openFile(file, true)
    }

    private fun forkSession(project: Project, session: SessionDisplay) {
        val file = ClaudeSessionVirtualFile("Fork of ${session.tabTitle}", forkFrom = session.sessionId).apply {
            if (session.worktreeName != null && project.basePath != null) {
                workingDir = ClaudePathEncoder.worktreeAbsolutePath(project.basePath!!, session.worktreeName)
                isWorktreeSession = true
            }
        }
        FileEditorManager.getInstance(project).openFile(file, true)
    }

    override fun shouldBeAvailable(project: Project): Boolean = project.basePath != null
}
