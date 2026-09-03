package com.canopy.editor

import com.canopy.model.ClaudeStatus
import com.canopy.services.ClaudeSessionService
import com.canopy.services.ClaudeStatusService
import com.canopy.services.ClaudeTerminalService
import com.canopy.services.OpenSessionsPersistence
import com.canopy.toolwindow.WorktreeInspector
import com.canopy.toolwindow.WorktreeState
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorWithTextEditors
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.terminal.JBTerminalWidget
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import com.pty4j.PtyProcess
import java.awt.BorderLayout
import java.awt.Color
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.*
import java.beans.PropertyChangeListener
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel
import com.canopy.util.RoundedProgressBarUI
import javax.swing.JProgressBar
import javax.swing.UIManager

class ClaudeSessionEditor(
    private val project: Project,
    val file: ClaudeSessionVirtualFile
) : UserDataHolderBase(), FileEditorWithTextEditors {

    private val log = Logger.getInstance(ClaudeSessionEditor::class.java)
    private val rootDisposable = Disposer.newDisposable("claude-editor-${file.sessionId ?: file.baseName}")
    private val loadingPanel = JBLoadingPanel(BorderLayout(), rootDisposable)
    private val loadingStopped = AtomicBoolean(false)
    private val isDisposed = AtomicBoolean(false)

    @Volatile private var tabShowing = false
    private var sessionDisposable: Disposable = Disposer.newDisposable(rootDisposable, "claude-session")
    private var currentContent: JComponent? = null
    private var focusComponent: JComponent? = null
    private var ptyProcess: PtyProcess? = null
    private var terminalWidget: JBTerminalWidget? = null
    private var endedBanner: JPanel? = null
    private var terminalArea: JPanel? = null
    @Volatile private var lastTitle: String? = null
    private var maxEffortButton: javax.swing.JButton? = null
    private var reconnectButton: javax.swing.JButton? = null
    private val contextProgress = JProgressBar(0, 100).apply {
        setUI(RoundedProgressBarUI())
        isStringPainted = true
        string = "Context: —"
        value = 0
        isOpaque = false
        border = JBUI.Borders.empty(5, 3)
        foreground = COLOR_GREEN
    }
    private val effortLabel = JBLabel("").apply {
        border = JBUI.Borders.empty(0, 6)
        foreground = UIManager.getColor("Label.disabledForeground")
    }
    private val thinkingLabel = JBLabel("").apply {
        border = JBUI.Borders.empty(0, 6)
        foreground = UIManager.getColor("Label.disabledForeground")
    }
    private val remainingLabel = JBLabel("").apply {
        border = JBUI.Borders.empty(0, 6)
        foreground = UIManager.getColor("Label.disabledForeground")
    }
    private val costLabel = JBLabel("").apply {
        border = JBUI.Borders.empty(0, 6)
        foreground = UIManager.getColor("Label.disabledForeground")
    }
    private val versionLabel = JBLabel("").apply {
        border = JBUI.Borders.empty(0, 6)
        foreground = UIManager.getColor("Label.disabledForeground")
        cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
        addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                val version = versionLinkTarget ?: return
                com.intellij.ide.BrowserUtil.browse("https://code.claude.com/docs/en/changelog#$version")
            }
        })
    }
    private var versionLinkTarget: String? = null
    private var hasContextBarData = false
    private var sessionToolbar: JComponent? = null
    private var gitToolbar: JComponent? = null
    private val contextBar = JPanel(BorderLayout()).apply {
        border = JBUI.Borders.empty(2, 4)
        val rightPanel = JPanel().apply {
            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.X_AXIS)
            isOpaque = false
            effortLabel.alignmentY = 0.5f
            thinkingLabel.alignmentY = 0.5f
            remainingLabel.alignmentY = 0.5f
            costLabel.alignmentY = 0.5f
            versionLabel.alignmentY = 0.5f
            add(effortLabel)
            add(thinkingLabel)
            add(remainingLabel)
            add(costLabel)
            add(versionLabel)
        }
        add(contextProgress, BorderLayout.CENTER)
        add(rightPanel, BorderLayout.EAST)
        isVisible = false
    }

    fun refreshChromeVisibility() {
        val settings = com.canopy.settings.CanopySettings.getInstance().state
        contextBar.isVisible = hasContextBarData && settings.showSessionContextBar
        sessionToolbar?.isVisible = settings.showSessionToolbar
        gitToolbar?.isVisible = settings.showGitToolbar
    }

    init {
        val sessionService = ClaudeSessionService.getInstance(project)
        val persistence = OpenSessionsPersistence.getInstance(project)

        tabShowing = loadingPanel.isShowing
        loadingPanel.addHierarchyListener { e ->
            if (e.changeFlags and java.awt.event.HierarchyEvent.SHOWING_CHANGED.toLong() != 0L) {
                tabShowing = loadingPanel.isShowing
            }
        }

        Disposer.register(rootDisposable, Disposable {
            if (com.canopy.services.CanopyShutdown.isClosing()) return@Disposable
            if (com.canopy.services.SessionStaging.isRearranging(project)) return@Disposable

            val runtimes = com.canopy.services.SessionRuntimeService.getInstance(project)
            if (file.isShellSession) return@Disposable runtimes.stop(file.sessionKey)

            file.sessionId?.let { persistence.remove(it) }
        })

        val nameListener: () -> Unit = {
            file.sessionId?.let { sid ->
                sessionService.getSessions().find { it.sessionId == sid }?.tabTitle?.let {
                    file.baseName = it
                    refreshTabTitle()
                }
            }
        }
        sessionService.addChangeListener(nameListener)
        Disposer.register(rootDisposable, Disposable { sessionService.removeChangeListener(nameListener) })

        val isResume = file.sessionId != null && file.forkFrom == null
        val isReattaching = com.canopy.services.SessionRuntimeService.getInstance(project)
            .existing(file.sessionId ?: file.sessionKey) != null

        if (!isReattaching) loadingPanel.startLoading()

        if (isResume && !isReattaching) {
            AppExecutorUtil.getAppScheduledExecutorService().execute {
                val externalIds = com.canopy.util.ClaudeProcessDetector
                    .detectExternalSessions(project.basePath, sessionService.getSessions())
                val isExternal = file.sessionId in externalIds
                ApplicationManager.getApplication().invokeLater {
                    if (project.isDisposed) return@invokeLater
                    if (isDisposed.get()) return@invokeLater

                    if (isExternal) {
                        refreshTabTitle()
                        showExternalPanel()
                    } else {
                        startTerminalSession()
                        file.sessionId?.let { persistence.add(it) }
                    }
                }
            }
        } else {
            startTerminalSession()
            if (isReattaching) file.sessionId?.let { persistence.add(it) }
        }
    }

    private fun showExternalPanel() {
        val panel = JPanel(java.awt.GridBagLayout())
        val inner = JPanel().apply {
            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(20)
        }

        val icon = com.intellij.ui.components.JBLabel(AllIcons.General.Information).apply {
            alignmentX = java.awt.Component.CENTER_ALIGNMENT
        }
        val message = com.intellij.ui.components.JBLabel("This session is open in an external terminal").apply {
            alignmentX = java.awt.Component.CENTER_ALIGNMENT
            font = font.deriveFont(font.size2D + 2f)
            border = JBUI.Borders.emptyTop(8)
        }
        val subtitle = com.intellij.ui.components.JBLabel("Close the external session first to resume here").apply {
            alignmentX = java.awt.Component.CENTER_ALIGNMENT
            foreground = javax.swing.UIManager.getColor("Label.disabledForeground")
            border = JBUI.Borders.emptyTop(4)
        }
        val resumeButton = javax.swing.JButton("Resume").apply {
            alignmentX = java.awt.Component.CENTER_ALIGNMENT
            isEnabled = false
            addActionListener {
                restartSession()
            }
        }

        inner.add(icon)
        inner.add(message)
        inner.add(subtitle)
        inner.add(javax.swing.Box.createVerticalStrut(JBUI.scale(16)))
        inner.add(resumeButton)

        panel.add(inner)
        currentContent = panel
        loadingPanel.add(panel, BorderLayout.CENTER)
        stopLoading()

        val sessionService = ClaudeSessionService.getInstance(project)
        val pollAlarm = com.intellij.util.Alarm(com.intellij.util.Alarm.ThreadToUse.POOLED_THREAD, sessionDisposable)
        lateinit var poll: Runnable
        poll = Runnable {
            val stillExternal = com.canopy.util.ClaudeProcessDetector
                .detectExternalSessions(project.basePath, sessionService.getSessions())
                .contains(file.sessionId)
            if (!stillExternal) {
                ApplicationManager.getApplication().invokeLater {
                    subtitle.text = "External session closed — ready to resume"
                    resumeButton.isEnabled = true
                }
            } else if (!pollAlarm.isDisposed) {
                pollAlarm.addRequest(poll, 3000)
            }
        }
        pollAlarm.addRequest(poll, 3000)
    }

    private fun startTerminalSession() {
        val terminalService = ClaudeTerminalService.getInstance(project)
        val statusService = ClaudeStatusService.getInstance(project)
        val sessionService = ClaudeSessionService.getInstance(project)
        val persistence = OpenSessionsPersistence.getInstance(project)

        val isFork = file.forkFrom != null
        val isNewWorktree = file.newWorktreeName != null
        val monitoringId = file.sessionId ?: "new-${System.nanoTime()}"
        val runtimeKey = file.sessionId ?: file.sessionKey
        val statusFile = statusService.createStatusFilePath(monitoringId)
        val notifyFile = statusService.createNotifyFilePath(monitoringId)

        val view = object : com.canopy.services.SessionRuntimeView {
            override fun onActiveChanged(isActive: Boolean) {
                if (isActive) {
                    stopLoading()
                    if (file.isUnresponsive) showAnswering(true)
                }
                refreshTabTitle()
            }

            override fun onUserInput() {
                statusService.clearNotifyState(file.sessionId ?: monitoringId)
                refreshTabTitle()
                maxEffortButton?.let { if (!it.isEnabled) it.isEnabled = true }
            }

            override fun onUnresponsive() = showAnswering(false)

            override fun onResponsive() = showAnswering(true)
        }

        val runtimes = com.canopy.services.SessionRuntimeService.getInstance(project)
        val running = runtimes.existing(runtimeKey)
        val runtime = running ?: runtimes.create(runtimeKey, file) { parent, relay ->
            when {
                file.isShellSession -> terminalService.createShellWidget(parent, workingDir = file.workingDir)
                isFork -> terminalService.createForkWidget(file.forkFrom!!, parent, workingDir = file.workingDir, statusFile = statusFile, notifyFile = notifyFile, onActiveChanged = relay::onActiveChanged, onUserInput = relay::onUserInput, onUnresponsive = relay::onUnresponsive, onResponsive = relay::onResponsive)
                file.sessionId != null -> terminalService.createResumeWidget(file.sessionId!!, parent, workingDir = file.workingDir, statusFile = statusFile, notifyFile = notifyFile, onActiveChanged = relay::onActiveChanged, onUserInput = relay::onUserInput, onUnresponsive = relay::onUnresponsive, onResponsive = relay::onResponsive)
                isNewWorktree -> terminalService.createNewWorktreeWidget(file.newWorktreeName!!, parent, workingDir = file.workingDir, statusFile = statusFile, notifyFile = notifyFile, onActiveChanged = relay::onActiveChanged, onUserInput = relay::onUserInput, onUnresponsive = relay::onUnresponsive, onResponsive = relay::onResponsive)
                else -> terminalService.createNewNamedSessionWidget(file.requestedName, parent, workingDir = file.workingDir, statusFile = statusFile, notifyFile = notifyFile, onActiveChanged = relay::onActiveChanged, onUserInput = relay::onUserInput, onUnresponsive = relay::onUnresponsive, onResponsive = relay::onResponsive)
            }
        }
        runtime.view = view
        Disposer.register(sessionDisposable, Disposable { if (runtime.view === view) runtime.view = null })

        val session = runtime.session
        ptyProcess = session.process
        focusComponent = session.widget.preferredFocusableComponent

        if (running == null) session.process.onExit().thenAccept { process ->
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                val code = process.exitValue()
                runtimes.stop(runtimeKey)
                if (code == 0) {
                    FileEditorManager.getInstance(project).closeFile(file)
                } else {
                    log.warn("Claude process exited with code $code for session ${file.sessionId}")
                    refreshTabTitle()
                    showProcessEnded(code)
                }
            }
        }

        val toolbar = createToolbar()
        sessionToolbar = toolbar

        terminalWidget = session.widget
        val terminalArea = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.emptyLeft(8)
            background = session.widget.terminalPanel.background
            add(session.widget.component, BorderLayout.CENTER)
        }

        this.terminalArea = terminalArea

        val gitDir = file.workingDir ?: project.basePath
        val isGitRepo = file.isWorktreeSession ||
            (gitDir != null && java.io.File(gitDir, ".git").let { it.isDirectory || it.isFile })

        val topPanel = JPanel().apply {
            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
            add(toolbar)
            if (isGitRepo) {
                gitToolbar = createGitToolbar { file.workingDir ?: project.basePath }
                add(gitToolbar)
            }
            if (file.isWorktreeSession) add(createWorktreeToolbar())
        }

        refreshChromeVisibility()

        val contentPanel = JPanel(BorderLayout())
        contentPanel.add(topPanel, BorderLayout.NORTH)
        contentPanel.add(terminalArea!!, BorderLayout.CENTER)
        contentPanel.add(contextBar, BorderLayout.SOUTH)

        currentContent = contentPanel
        loadingPanel.add(contentPanel, BorderLayout.CENTER)

        setupDropTarget(session.widget.component)

        if (running == null) {
            loadingPanel.startLoading()
            AppExecutorUtil.getAppScheduledExecutorService()
                .schedule(::stopLoading, 5L, TimeUnit.SECONDS)
        }

        if (running == null && !file.isShellSession) {
            statusService.startMonitoring(monitoringId, statusFile, notifyFile)
            Disposer.register(runtime.disposable, Disposable { statusService.stopMonitoring(monitoringId) })
        }

        val statusListener: (String, ClaudeStatus?) -> Unit = { sid, status ->
            if (sid == monitoringId || sid == file.sessionId) ApplicationManager.getApplication().invokeLater {
                if (isDisposed.get()) return@invokeLater

                file.modelId = status?.modelId
                file.modelName = status?.modelName
                file.contextPercent = status?.contextRemainingPercent
                val selectedFile = FileEditorManager.getInstance(project).selectedEditor?.file
                if (statusService.getNotifyState(sid) == "idle_prompt" && selectedFile == file) {
                    statusService.clearNotifyState(file.sessionId ?: monitoringId)
                }
                refreshTabTitle()
                updateContextBar(status)

            }
        }
        statusService.addStatusListener(statusListener)
        Disposer.register(sessionDisposable, Disposable { statusService.removeStatusListener(statusListener) })
        statusService.getStatus(monitoringId)?.let { statusListener(monitoringId, it) }

        val selectionListener = object : FileEditorManagerListener {
            override fun selectionChanged(event: com.intellij.openapi.fileEditor.FileEditorManagerEvent) {
                val id = file.sessionId ?: monitoringId
                if (event.newFile == file && statusService.getNotifyState(id) == "idle_prompt") {
                    statusService.clearNotifyState(id)
                    refreshTabTitle()
                }
            }

            override fun fileClosed(source: FileEditorManager, closedFile: VirtualFile) {
                if (closedFile != file) return
                if (runtimes.existing(file.sessionId ?: file.sessionKey) != null) return

                statusService.clearSession(monitoringId)
                file.sessionId?.let { if (it != monitoringId) statusService.clearSession(it) }
            }
        }
        project.messageBus.connect(sessionDisposable).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER, selectionListener
        )

        if (running == null && !file.isShellSession) {
            followReportedSession(sessionService, statusService, persistence, monitoringId, statusFile, notifyFile)
        }
    }

    private fun createToolbar(): JComponent {
        val leftPanel = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 2))

        leftPanel.add(createModelDropdown())

        maxEffortButton = javax.swing.JButton("max").apply {
            toolTipText = "Send /effort max"
            isFocusable = false
            addActionListener {
                sendToTerminal("/effort max\r")
                isEnabled = false
            }
        }
        leftPanel.add(maxEffortButton)

        val forkButton = javax.swing.JButton(com.intellij.icons.AllIcons.Actions.Copy)
        forkButton.toolTipText = "Fork — open a new session branched from this one"
        forkButton.isFocusable = false
        forkButton.addActionListener {
            val sessionId = file.sessionId ?: return@addActionListener
            val forkFile = ClaudeSessionVirtualFile("Fork of ${file.baseName}", forkFrom = sessionId).apply {
                workingDir = file.workingDir
            }
            FileEditorManager.getInstance(project).openFile(forkFile, true)
        }
        leftPanel.add(forkButton)

        reconnectButton = javax.swing.JButton(com.intellij.icons.AllIcons.Actions.Refresh).apply {
            toolTipText = "Reconnect — close and resume this session"
            isFocusable = false
            addActionListener {
                if (file.sessionId == null) return@addActionListener
                restartSession()
            }
        }
        leftPanel.add(reconnectButton)

        val deleteButton = javax.swing.JButton(AllIcons.Actions.GC)
        deleteButton.toolTipText = "Delete this session"
        deleteButton.isFocusable = false
        deleteButton.addActionListener {
            val sessionId = file.sessionId ?: return@addActionListener
            val confirm = com.intellij.openapi.ui.Messages.showYesNoDialog(
                project,
                "Delete session \"${file.baseName}\"?\n\nThis cannot be undone.",
                "Delete Session",
                AllIcons.General.Warning
            )
            if (confirm != com.intellij.openapi.ui.Messages.YES) return@addActionListener
            val manager = FileEditorManager.getInstance(project)
            manager.closeFile(file)
            ClaudeSessionService.getInstance(project).deleteSession(sessionId)
        }
        leftPanel.add(deleteButton)

        val rightPanel = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 4, 2))

        val toolbar = JPanel(BorderLayout())
        toolbar.add(leftPanel, BorderLayout.WEST)
        toolbar.add(rightPanel, BorderLayout.EAST)
        return toolbar
    }

    private fun transcriptPath(): Path? {
        val sessionId = file.sessionId ?: return null
        val basePath = project.basePath ?: return null
        val workingDir = file.workingDir
        val projectDir = if (file.isWorktreeSession && workingDir != null) {
            val wtName = Path.of(workingDir).fileName.toString()
            com.canopy.util.ClaudePathEncoder.worktreeProjectDir(basePath, wtName)
        } else {
            com.canopy.util.ClaudePathEncoder.projectDir(basePath)
        }
        return projectDir.resolve("$sessionId.jsonl")
    }

    private fun createGitToolbar(resolveGitDir: () -> String?): JComponent {
        val branchLabel = JBLabel(AllIcons.Vcs.Branch).apply {
            border = JBUI.Borders.empty(0, 4)
        }
        val branchName = JBLabel("").apply {
            border = JBUI.Borders.empty(0, 4)
            foreground = UIManager.getColor("Label.disabledForeground")
        }
        val statsLabel = JBLabel("").apply {
            border = JBUI.Borders.empty(0, 4)
            foreground = UIManager.getColor("Label.disabledForeground")
        }

        val commitButton = javax.swing.JButton("Commit").apply {
            isFocusable = false
            isEnabled = false
            toolTipText = "No session changes to commit"
        }

        val gate = com.canopy.util.RefreshGate(TOOLBAR_REFRESH_FLOOR_MS)
        fun refresh(isForced: Boolean = false) {
            val gitDir = resolveGitDir() ?: return
            if (!java.io.File(gitDir, ".git").exists()) return
            if (!gate.tryStart(isForced)) return
            com.canopy.util.CanopyExecutor.submit {
                try {
                    val branch = execGit(gitDir, "branch", "--show-current").trim()
                    val status = execGit(gitDir, "status", "--porcelain").trim()
                    val changedFiles = if (status.isEmpty()) emptyList() else status.lines()
                        .filter { it.isNotBlank() }
                        .mapNotNull { line ->
                            val path = line.drop(3).split(" -> ").last().trim()
                            if (path.isNotEmpty()) java.nio.file.Path.of(gitDir, path).toAbsolutePath().toString() else null
                        }
                    val changed = changedFiles.size

                    val writes = writesSoFar()
                    val sessionFiles = writes.mapTo(HashSet()) { it.path }
                    val bySession = if (sessionFiles.isNotEmpty()) {
                        changedFiles.count { it in sessionFiles }
                    } else 0

                    val pureFiles = ownedChangedFiles(gitDir, writes)

                    ApplicationManager.getApplication().invokeLater {
                        branchName.text = branch
                        statsLabel.text = when {
                            changed == 0 -> "clean"
                            bySession > 0 -> "$changed changed ($bySession by session)"
                            else -> "$changed file${if (changed > 1) "s" else ""} changed"
                        }
                        statsLabel.foreground = if (changed > 0) COLOR_YELLOW else UIManager.getColor("Label.disabledForeground")
                        commitButton.isEnabled = pureFiles.isNotEmpty()
                        commitButton.toolTipText = when {
                            pureFiles.isNotEmpty() -> "Commit ${pureFiles.size} session file${if (pureFiles.size > 1) "s" else ""}"
                            bySession > 0 -> "Files have mixed changes from other sources"
                            else -> "No session changes to commit"
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    gate.finish()
                }
            }
        }

        refresh(isForced = true)

        val statusService = ClaudeStatusService.getInstance(project)
        val listener: (String, ClaudeStatus?) -> Unit = { sid, _ ->
            if (sid == file.sessionId) refresh()
        }
        statusService.addStatusListener(listener)
        Disposer.register(sessionDisposable, Disposable { statusService.removeStatusListener(listener) })

        wireToolbarRefresh(::refresh)
        wireNewWorktreeDirPoll(::refresh)

        commitButton.addActionListener {
            val gitDir = resolveGitDir() ?: return@addActionListener
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    val pureFiles = ownedChangedFiles(gitDir, writesSoFar())
                    if (pureFiles.isEmpty()) return@executeOnPooledThread

                    for (f in pureFiles) {
                        val rel = java.nio.file.Path.of(gitDir).relativize(java.nio.file.Path.of(f)).toString()
                        execGitWithExitCode(gitDir, "add", rel)
                    }

                    val fileList = pureFiles.joinToString(", ") {
                        java.nio.file.Path.of(gitDir).relativize(java.nio.file.Path.of(it)).toString()
                    }
                    ApplicationManager.getApplication().invokeLater {
                        sendToTerminal("commit the staged changes to: $fileList\r")
                    }
                } catch (e: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        showNotification("Commit failed: ${e.message}", NotificationType.ERROR)
                    }
                }
            }
        }

        branchLabel.alignmentY = java.awt.Component.CENTER_ALIGNMENT
        branchName.alignmentY = java.awt.Component.CENTER_ALIGNMENT
        statsLabel.alignmentY = java.awt.Component.CENTER_ALIGNMENT

        val leftPanel = JPanel().apply {
            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.X_AXIS)
            border = JBUI.Borders.empty(2, 4)
            add(branchLabel)
            add(branchName)
            add(javax.swing.Box.createHorizontalStrut(8))
            add(statsLabel)
            add(javax.swing.Box.createHorizontalStrut(8))
            add(commitButton)
        }

        val bar = JPanel(BorderLayout())
        bar.add(leftPanel, BorderLayout.WEST)
        return bar
    }

    private fun createWorktreeToolbar(): JComponent {
        val projectPath = project.basePath

        val branchLabel = JBLabel(ClaudeSessionIconProvider.TREE_ICON).apply {
            border = JBUI.Borders.empty(0, 4)
        }
        val statusLabel = JBLabel("").apply {
            border = JBUI.Borders.empty(0, 4)
            foreground = UIManager.getColor("Label.disabledForeground")
        }

        var currentWtBranch = ""
        var currentMainBranch = ""
        var currentAhead = 0
        var currentBehind = 0
        var hasUncommitted = false

        val commitButton = javax.swing.JButton("Commit").apply {
            isFocusable = false
            isEnabled = false
            toolTipText = "No uncommitted changes"
        }
        val updateButton = javax.swing.JButton(UPDATE_LABEL).apply {
            isFocusable = false
            isEnabled = false
            toolTipText = "Up to date"
        }
        val mergeButton = javax.swing.JButton(MERGE_LABEL).apply {
            isFocusable = false
            isEnabled = false
            toolTipText = "Nothing to merge"
        }

        val openInIdeButton = javax.swing.JButton("Open in IDE").apply {
            isFocusable = false
            addActionListener {
                file.workingDir?.let { wt ->
                    com.intellij.ide.impl.ProjectUtil.openOrImport(java.nio.file.Path.of(wt), null, true)
                }
            }
        }

        val openInTerminalButton = javax.swing.JButton("Open in Terminal").apply {
            isFocusable = false
            addActionListener {
                val worktreePath = file.workingDir ?: return@addActionListener
                try {
                    val terminal = org.jetbrains.plugins.terminal.TerminalToolWindowManager.getInstance(project)
                    val runner = org.jetbrains.plugins.terminal.LocalTerminalDirectRunner.createTerminalRunner(project)
                    val tabState = org.jetbrains.plugins.terminal.TerminalTabState().apply {
                        myTabName = worktreePath.substringAfterLast('/')
                        myWorkingDirectory = worktreePath
                    }
                    terminal.createNewSession(runner, tabState)
                } catch (ex: Exception) {
                    log.warn("Failed to open terminal at $worktreePath", ex)
                    showNotification("Failed to open terminal: ${ex.message}", NotificationType.ERROR)
                }
            }
        }

        val revealButton = javax.swing.JButton(AllIcons.Actions.MenuOpen).apply {
            isFocusable = false
            addActionListener {
                file.workingDir?.let { wt ->
                    com.intellij.ide.actions.RevealFileAction.openDirectory(java.io.File(wt))
                }
            }
        }

        val showChangesButton = javax.swing.JButton("Show Changes").apply {
            isFocusable = false
            addActionListener {
                val wt = file.workingDir ?: return@addActionListener
                com.canopy.util.CanopyExecutor.submit {
                    val result = com.canopy.toolwindow.WorktreeChanges.compute(wt)
                    ApplicationManager.getApplication().invokeLater {
                        if (result.changes.isEmpty()) {
                            showNotification(result.error ?: "No changes in this worktree.", NotificationType.INFORMATION)
                        } else {
                            com.canopy.toolwindow.WorktreeChanges.openDialog(
                                project, "Changes — ${wt.substringAfterLast('/')}", wt, result.changes
                            )
                        }
                    }
                }
            }
        }

        fun updateOpenButtons() {
            val hasPath = file.workingDir != null
            val noPathTip = "Available after the first message — the worktree hasn't been created yet"
            openInIdeButton.isEnabled = hasPath
            openInIdeButton.toolTipText = if (hasPath) "Open worktree directory as a separate project" else noPathTip
            openInTerminalButton.isEnabled = hasPath
            openInTerminalButton.toolTipText = if (hasPath) "Open a terminal tab in this IDE rooted at the worktree directory" else noPathTip
            revealButton.isEnabled = hasPath
            revealButton.toolTipText = if (hasPath) "Reveal worktree directory in file manager" else noPathTip
            showChangesButton.isEnabled = hasPath
            showChangesButton.toolTipText = if (hasPath) "Show this worktree's diff (committed vs base + uncommitted)" else noPathTip
        }
        updateOpenButtons()

        val gate = com.canopy.util.RefreshGate(TOOLBAR_REFRESH_FLOOR_MS)
        fun refreshBranchStatus(isForced: Boolean = false) {
            ApplicationManager.getApplication().invokeLater { updateOpenButtons() }
            val worktreePath = file.workingDir
            if (worktreePath == null || projectPath == null) return
            if (file.sessionId == null && !java.nio.file.Files.isDirectory(java.nio.file.Path.of(worktreePath))) {
                ApplicationManager.getApplication().invokeLater {
                    statusLabel.text = worktreePath.substringAfterLast('/') + "  — creating…"
                    statusLabel.foreground = UIManager.getColor("Label.disabledForeground")
                    statusLabel.toolTipText = "Setting up the worktree…"
                }
                return
            }
            if (!gate.tryStart(isForced)) return
            com.canopy.util.CanopyExecutor.submit {
                val info = try {
                    WorktreeInspector.status(worktreePath, projectPath)
                } finally {
                    gate.finish()
                }
                ApplicationManager.getApplication().invokeLater {
                    info.wtBranch?.let { currentWtBranch = it }
                    info.mainBranch?.let { currentMainBranch = it }
                    currentAhead = info.ahead
                    currentBehind = info.behind
                    hasUncommitted = info.dirty

                    statusLabel.text = buildString {
                        append(info.name)
                        when (info.state) {
                            WorktreeState.REBASING -> append("  \u2014 REBASING")
                            WorktreeState.MERGING -> append("  \u2014 MERGING")
                            WorktreeState.CHERRY_PICKING -> append("  \u2014 CHERRY-PICKING")
                            WorktreeState.DETACHED -> append("  \u2014 detached HEAD")
                            WorktreeState.MISSING -> append("  \u2014 missing")
                            WorktreeState.UNKNOWN -> append("  \u2014 status unavailable")
                            WorktreeState.OK -> {
                                if (info.ahead > 0 || info.behind > 0) {
                                    append("  ")
                                    if (info.ahead > 0) append("\u2191${info.ahead}")
                                    if (info.ahead > 0 && info.behind > 0) append(" ")
                                    if (info.behind > 0) append("\u2193${info.behind}")
                                    info.mainBranch?.let { append(" vs $it") }
                                }
                            }
                        }
                    }
                    statusLabel.foreground = if (info.state == WorktreeState.OK)
                        UIManager.getColor("Label.disabledForeground") else JBColor.RED
                    statusLabel.toolTipText = when (info.state) {
                        WorktreeState.UNKNOWN -> "Status unavailable: ${info.errorDetail ?: "unknown error"}"
                        WorktreeState.MISSING -> info.errorDetail
                        else -> null
                    }

                    if (info.state != WorktreeState.OK) {
                        val frozenTip = when (info.state) {
                            WorktreeState.REBASING -> "Rebase in progress \u2014 resolve in the terminal with git rebase --continue or --abort"
                            WorktreeState.MERGING -> "Merge in progress \u2014 resolve in the terminal with git merge --continue or --abort"
                            WorktreeState.CHERRY_PICKING -> "Cherry-pick in progress \u2014 resolve in the terminal with git cherry-pick --continue or --abort"
                            WorktreeState.DETACHED -> "Detached HEAD \u2014 check out a branch first"
                            WorktreeState.MISSING -> "Worktree directory not found"
                            WorktreeState.UNKNOWN -> "Status unavailable: ${info.errorDetail ?: "unknown error"}"
                            WorktreeState.OK -> ""
                        }
                        listOf(commitButton, updateButton, mergeButton).forEach {
                            it.isEnabled = false
                            it.toolTipText = frozenTip
                        }
                    } else {
                        val wtBranch = info.wtBranch ?: ""
                        val mainBranch = info.mainBranch ?: ""

                        updateButton.isEnabled = info.behind > 0 && !info.dirty
                        updateButton.toolTipText = when {
                            info.dirty -> "Commit or stash worktree changes first"
                            info.behind > 0 -> "Rebase $wtBranch onto ${info.behind} new commit${if (info.behind > 1) "s" else ""} from $mainBranch"
                            else -> "Up to date with $mainBranch"
                        }

                        mergeButton.isEnabled = info.ahead > 0 && info.behind == 0
                        mergeButton.toolTipText = when {
                            info.ahead == 0 -> "Nothing to merge"
                            info.behind > 0 -> "Update worktree first \u2014 $mainBranch has diverged"
                            else -> "Fast-forward ${info.ahead} commit${if (info.ahead > 1) "s" else ""} into $mainBranch"
                        }

                        commitButton.isEnabled = info.dirty
                        commitButton.toolTipText = if (info.dirty) "Ask Claude to commit changes" else "No uncommitted changes"
                    }
                }
            }
        }

        commitButton.addActionListener {
            commitButton.isEnabled = false
            sendToTerminal("commit all changes with a descriptive commit message\r")
        }

        updateButton.addActionListener {
            val worktreePath = file.workingDir ?: return@addActionListener
            updateButton.isEnabled = false
            updateButton.text = UPDATE_LABEL + BUSY_SUFFIX
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    val result = execGitWithExitCode(worktreePath, "rebase", currentMainBranch)
                    ApplicationManager.getApplication().invokeLater {
                        when {
                            result.exitCode == 0 -> {
                                showNotification(
                                    "Rebased $currentWtBranch onto $currentMainBranch",
                                    NotificationType.INFORMATION
                                )
                                com.intellij.openapi.vfs.VirtualFileManager.getInstance().asyncRefresh {}
                            }
                            result.output.contains("CONFLICT") -> showNotification(
                                "Resolve in the terminal with git rebase --continue or --abort.",
                                NotificationType.WARNING,
                                "Update stopped on conflicts"
                            )
                            else -> showNotification(
                                gitFailureMessage(result),
                                NotificationType.ERROR,
                                "Update failed"
                            )
                        }
                    }
                } catch (e: Exception) {
                    log.warn("Update (rebase) failed for $worktreePath", e)
                    ApplicationManager.getApplication().invokeLater {
                        showNotification(
                            e.message ?: e.javaClass.simpleName,
                            NotificationType.ERROR,
                            "Update failed"
                        )
                    }
                } finally {
                    ApplicationManager.getApplication().invokeLater { updateButton.text = UPDATE_LABEL }
                    refreshBranchStatus(isForced = true)
                }
            }
        }

        mergeButton.addActionListener {
            if (projectPath == null) return@addActionListener
            mergeButton.isEnabled = false
            mergeButton.text = MERGE_LABEL + BUSY_SUFFIX
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    val result = execGitWithExitCode(projectPath, "merge", "--ff-only", currentWtBranch)
                    ApplicationManager.getApplication().invokeLater {
                        if (result.exitCode == 0) {
                            showNotification(
                                "Merged $currentWtBranch into $currentMainBranch (fast-forward)",
                                NotificationType.INFORMATION
                            )
                            com.intellij.openapi.vfs.VirtualFileManager.getInstance().asyncRefresh {}
                        } else {
                            showNotification(
                                gitFailureMessage(result),
                                NotificationType.ERROR,
                                "Merge to project failed"
                            )
                        }
                    }
                } catch (e: Exception) {
                    log.warn("Merge to project failed for $currentWtBranch", e)
                    ApplicationManager.getApplication().invokeLater {
                        showNotification(
                            e.message ?: e.javaClass.simpleName,
                            NotificationType.ERROR,
                            "Merge to project failed"
                        )
                    }
                } finally {
                    ApplicationManager.getApplication().invokeLater { mergeButton.text = MERGE_LABEL }
                    refreshBranchStatus(isForced = true)
                }
            }
        }

        refreshBranchStatus(isForced = true)

        val statusService = ClaudeStatusService.getInstance(project)
        val listener: (String, com.canopy.model.ClaudeStatus?) -> Unit = { sid, _ ->
            if (sid == file.sessionId) refreshBranchStatus()
        }
        statusService.addStatusListener(listener)
        Disposer.register(sessionDisposable, Disposable { statusService.removeStatusListener(listener) })

        wireToolbarRefresh(::refreshBranchStatus)
        wireNewWorktreeDirPoll(::refreshBranchStatus)

        branchLabel.alignmentY = java.awt.Component.CENTER_ALIGNMENT
        statusLabel.alignmentY = java.awt.Component.CENTER_ALIGNMENT
        commitButton.alignmentY = java.awt.Component.CENTER_ALIGNMENT
        updateButton.alignmentY = java.awt.Component.CENTER_ALIGNMENT
        mergeButton.alignmentY = java.awt.Component.CENTER_ALIGNMENT

        val leftPanel = JPanel().apply {
            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.X_AXIS)
            border = JBUI.Borders.empty(2, 4)
            add(branchLabel)
            add(statusLabel)
            add(javax.swing.Box.createHorizontalStrut(8))
            add(commitButton)
            add(javax.swing.Box.createHorizontalStrut(8))
            add(updateButton)
            add(javax.swing.Box.createHorizontalStrut(4))
            add(mergeButton)
        }

        showChangesButton.alignmentY = java.awt.Component.CENTER_ALIGNMENT
        openInIdeButton.alignmentY = java.awt.Component.CENTER_ALIGNMENT
        openInTerminalButton.alignmentY = java.awt.Component.CENTER_ALIGNMENT
        revealButton.alignmentY = java.awt.Component.CENTER_ALIGNMENT

        val rightPanel = JPanel().apply {
            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.X_AXIS)
            border = JBUI.Borders.empty(2, 4)
            add(showChangesButton)
            add(javax.swing.Box.createHorizontalStrut(4))
            add(openInIdeButton)
            add(javax.swing.Box.createHorizontalStrut(4))
            add(openInTerminalButton)
            add(javax.swing.Box.createHorizontalStrut(4))
            add(revealButton)
        }

        val bar = JPanel(BorderLayout())
        bar.add(leftPanel, BorderLayout.WEST)
        bar.add(rightPanel, BorderLayout.EAST)
        return bar
    }

    private fun wireToolbarRefresh(refresh: (isForced: Boolean) -> Unit) {
        val refreshAlarm = com.intellij.util.Alarm(com.intellij.util.Alarm.ThreadToUse.POOLED_THREAD, sessionDisposable)
        lateinit var tick: Runnable
        tick = Runnable {
            if (tabShowing) refresh(false)
            val sec = com.canopy.settings.CanopySettings.getInstance().state.branchStatusRefreshSeconds
            if (sec > 0 && !refreshAlarm.isDisposed) {
                refreshAlarm.addRequest(tick, sec * 1000)
            }
        }
        val initialSec = com.canopy.settings.CanopySettings.getInstance().state.branchStatusRefreshSeconds
        if (initialSec > 0) {
            refreshAlarm.addRequest(tick, initialSec * 1000)
        }

        val selectionListener = object : FileEditorManagerListener {
            override fun selectionChanged(event: com.intellij.openapi.fileEditor.FileEditorManagerEvent) {
                if (event.newFile == file) refresh(true)
            }
        }
        project.messageBus.connect(sessionDisposable).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER, selectionListener
        )
    }

    private fun wireNewWorktreeDirPoll(refresh: (isForced: Boolean) -> Unit) {
        val worktreeDir = file.workingDir ?: return
        if (file.sessionId != null) return
        if (java.io.File(worktreeDir).isDirectory) return

        val alarm = com.intellij.util.Alarm(com.intellij.util.Alarm.ThreadToUse.POOLED_THREAD, sessionDisposable)
        val attempts = java.util.concurrent.atomic.AtomicInteger(0)
        lateinit var tick: Runnable
        tick = Runnable {
            refresh(true)
            val done = java.io.File(worktreeDir).isDirectory ||
                file.sessionId != null ||
                attempts.incrementAndGet() >= 40
            if (!done && !alarm.isDisposed) {
                alarm.addRequest(tick, 750)
            }
        }
        if (!alarm.isDisposed) alarm.addRequest(tick, 750)
    }

    private fun showNotification(message: String, type: NotificationType, title: String = "") {
        val group = if (type == NotificationType.ERROR || type == NotificationType.WARNING) {
            "Canopy Errors"
        } else {
            "Canopy"
        }
        NotificationGroupManager.getInstance()
            .getNotificationGroup(group)
            .createNotification(title, message, type)
            .notify(project)
    }

    private data class GitResult(val exitCode: Int, val output: String, val timedOut: Boolean = false)

    private fun execGit(workDir: String, vararg args: String): String {
        return execGitWithExitCode(workDir, *args).output
    }

    private fun execGitWithExitCode(workDir: String, vararg args: String): GitResult {
        val res = com.canopy.util.ProcessHelper.execWithTimeout(
            command = arrayOf("git", "-C", workDir, *args),
            timeoutMs = GIT_TIMEOUT_MS,
            extraEnv = mapOf("GIT_OPTIONAL_LOCKS" to "0")
        )
        return GitResult(res.exitCode, res.output, res.timedOut)
    }

    private fun gitFailureMessage(result: GitResult): String {
        if (result.timedOut) {
            return "Timed out after ${GIT_TIMEOUT_MS / 1000}s. The repository may be busy, " +
                "or git may be waiting on credentials."
        }

        val output = result.output.trim()
        val hint = GIT_FAILURE_HINTS.firstOrNull { (pattern, _) ->
            output.contains(pattern, ignoreCase = true)
        }?.second

        val detail = if (output.isEmpty()) {
            "git exited with code ${result.exitCode} and produced no output."
        } else {
            output.take(GIT_OUTPUT_LIMIT) + if (output.length > GIT_OUTPUT_LIMIT) "…" else ""
        }

        val escaped = com.intellij.openapi.util.text.StringUtil.escapeXmlEntities(detail)
            .replace("\n", "<br/>")
        return if (hint != null) "$hint<br/><br/>$escaped" else escaped
    }

    private val transcriptJson = com.google.gson.Gson()
    private val writesTail = com.canopy.util.JsonlTailReader()
    private val sessionWrites = mutableListOf<com.canopy.session.FileWrite>()
    private var writesFrom: java.nio.file.Path? = null

    @Synchronized
    private fun writesSoFar(): List<com.canopy.session.FileWrite> {
        val transcript = transcriptPath()?.takeIf { java.nio.file.Files.exists(it) } ?: return emptyList()

        if (transcript != writesFrom) {
            writesFrom = transcript
            writesTail.reset()
            sessionWrites.clear()
        }

        runCatching {
            writesTail.consume(transcript, onRestart = sessionWrites::clear) { line ->
                sessionWrites += com.canopy.session.fileWritesIn(
                    transcriptJson.fromJson(line, com.google.gson.JsonObject::class.java)
                )
            }
        }

        return sessionWrites.toList()
    }

    private fun ownedChangedFiles(gitDir: String, writes: List<com.canopy.session.FileWrite>): List<String> {
        if (writes.isEmpty()) return emptyList()

        val root = java.nio.file.Path.of(gitDir)
        val changed = execGit(gitDir, "status", "--porcelain")
            .lineSequence()
            .filter { it.isNotBlank() }
            .map { it.drop(3).substringAfterLast(" -> ").trim() }
            .filter { it.isNotEmpty() }
            .mapTo(HashSet()) { root.resolve(it).toAbsolutePath().toString() }

        return com.canopy.session.ownedFiles(
            changed = changed,
            writes = writes,
            headOf = { path ->
                val relative = root.relativize(java.nio.file.Path.of(path)).toString()
                execGitWithExitCode(gitDir, "show", "HEAD:$relative").let { if (it.exitCode == 0) it.output else "" }
            },
            currentOf = { path -> runCatching { java.nio.file.Files.readString(java.nio.file.Path.of(path)) }.getOrNull() },
        )
    }

    private fun createModelDropdown(): JComponent {
        val aliases = arrayOf("sonnet", "opus", "haiku", "sonnet[1m]", "opus[1m]", "opusplan")

        fun idToAlias(modelId: String): String? {
            val id = modelId.lowercase()
            val has1m = id.contains("[1m]")
            return when {
                id.contains("opus") -> if (has1m) "opus[1m]" else "opus"
                id.contains("sonnet") -> if (has1m) "sonnet[1m]" else "sonnet"
                id.contains("haiku") -> "haiku"
                else -> null
            }
        }

        val combo = javax.swing.JComboBox(aliases)
        combo.isFocusable = false
        var updating = false

        file.modelId?.let { idToAlias(it) }?.let { combo.selectedItem = it }

        combo.addActionListener {
            if (updating) return@addActionListener
            val selected = combo.selectedItem as? String ?: return@addActionListener
            sendToTerminal("/model $selected\r")
        }

        val statusService = ClaudeStatusService.getInstance(project)
        val listener: (String, ClaudeStatus?) -> Unit = listener@{ sid, status ->
            val monitoringId = file.sessionId ?: return@listener
            if (sid != monitoringId) return@listener
            val matched = status?.modelId?.let { idToAlias(it) } ?: return@listener
            ApplicationManager.getApplication().invokeLater {
                if (combo.selectedItem != matched) {
                    updating = true
                    combo.selectedItem = matched
                    updating = false
                }
            }
        }
        statusService.addStatusListener(listener)
        Disposer.register(sessionDisposable, Disposable { statusService.removeStatusListener(listener) })

        return combo
    }

    private fun setupDropTarget(component: JComponent) {
        val highlightColor = JBUI.CurrentTheme.Focus.focusColor()
        val highlightBorder = BorderFactory.createLineBorder(highlightColor, 2)
        val originalBorder = component.border

        DropTarget(component, DnDConstants.ACTION_COPY, object : DropTargetAdapter() {
            override fun dragEnter(dtde: DropTargetDragEvent) {
                if (dtde.transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY)
                    component.border = highlightBorder
                    component.repaint()
                } else {
                    dtde.rejectDrag()
                }
            }

            override fun dragExit(dte: DropTargetEvent) {
                component.border = originalBorder
                component.repaint()
            }

            override fun drop(dtde: DropTargetDropEvent) {
                component.border = originalBorder
                component.repaint()

                if (!dtde.transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    dtde.rejectDrop()
                    return
                }
                dtde.acceptDrop(DnDConstants.ACTION_COPY)
                try {
                    @Suppress("UNCHECKED_CAST")
                    val files = dtde.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
                    val paths = files.map { it.absolutePath }
                    if (paths.isNotEmpty()) {
                        sendToTerminal(paths.joinToString(" "))
                    }
                    dtde.dropComplete(true)
                } catch (e: Exception) {
                    log.warn("Failed to handle file drop", e)
                    dtde.dropComplete(false)
                }
            }
        })
    }

    private fun showProcessEnded(code: Int) {
        if (endedBanner != null) return
        val reason = if (code == 143) "The agent was stopped." else "The agent exited with code $code."

        val restart = javax.swing.JButton("Restart Session").apply {
            addActionListener {
                val manager = FileEditorManager.getInstance(project)
                val reopened = ClaudeSessionVirtualFile(file.baseName, sessionId = file.sessionId).also {
                    it.workingDir = file.workingDir
                    it.isWorktreeSession = file.isWorktreeSession
                }
                manager.closeFile(file)
                manager.openFile(reopened, true)
            }
        }
        val banner = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, JBUI.scale(10), JBUI.scale(6))).apply {
            background = com.intellij.util.ui.UIUtil.getPanelBackground()
            border = JBUI.Borders.customLine(com.canopy.insight.InsightUi.needsAttention(), 0, 0, 1, 0)
            add(com.intellij.ui.components.JBLabel("$reason This terminal no longer accepts input."))
            add(restart)
        }

        endedBanner = banner
        currentContent?.add(banner, BorderLayout.NORTH)
        currentContent?.revalidate()
        currentContent?.repaint()
    }

    fun terminal(): JBTerminalWidget? = terminalWidget

    fun sendToTerminal(text: String) {
        val process = ptyProcess ?: return
        if (!process.isAlive) return
        try {
            process.outputStream.write(text.toByteArray(StandardCharsets.UTF_8))
            process.outputStream.flush()
        } catch (e: Exception) {
            log.warn("Failed to send input to terminal", e)
        }
    }

    /**
     * The terminal says which session it is showing, so nothing has to be worked out from the
     * transcripts: guessing there could not see a session that already existed when the tab opened,
     * which is every conversation the CLI resumes rather than starts.
     */
    private fun followReportedSession(
        sessionService: ClaudeSessionService,
        statusService: ClaudeStatusService,
        persistence: OpenSessionsPersistence,
        tempMonitoringId: String,
        tempStatusFile: Path,
        notifyFile: Path
    ) {
        val listener: (String, ClaudeStatus?) -> Unit = { sid, status ->
            if (sid == tempMonitoringId || sid == file.sessionId) {
                sessionIdToLink(status?.reportedSessionId, file.sessionId)?.let { reported ->
                    ApplicationManager.getApplication().invokeLater {
                        if (!isDisposed.get()) {
                            linkTo(reported, sessionService, statusService, persistence, tempMonitoringId, tempStatusFile, notifyFile)
                        }
                    }
                }
            }
        }
        statusService.addStatusListener(listener)
        Disposer.register(sessionDisposable, Disposable { statusService.removeStatusListener(listener) })
        statusService.getStatus(tempMonitoringId)?.let { listener(tempMonitoringId, it) }
    }

    private fun linkTo(
        sessionId: String,
        sessionService: ClaudeSessionService,
        statusService: ClaudeStatusService,
        persistence: OpenSessionsPersistence,
        tempMonitoringId: String,
        tempStatusFile: Path,
        notifyFile: Path
    ) {
        if (file.sessionId == sessionId) return

        val previous = file.sessionId
        file.sessionId = sessionId
        sessionService.getSessions().firstOrNull { it.sessionId == sessionId }?.let { session ->
            file.baseName = session.tabTitle
            if (file.workingDir == null && session.worktreeName != null && project.basePath != null) {
                file.workingDir = com.canopy.util.ClaudePathEncoder
                    .worktreeAbsolutePath(project.basePath!!, session.worktreeName)
            }
        }

        previous?.let(persistence::remove)
        persistence.add(sessionId)
        com.canopy.services.SessionRuntimeService.getInstance(project).rekey(previous ?: file.sessionKey, sessionId)
        com.canopy.services.rememberOpenTerminals(project, "sessionLinked")

        // The env named the temp files at spawn, so the agent writes to them whatever it is called now.
        statusService.stopMonitoring(tempMonitoringId)
        previous?.let(statusService::stopMonitoring)
        statusService.startMonitoring(sessionId, tempStatusFile, notifyFile)

        refreshTabTitle(force = true)
        // The row for this tab is keyed on the tab, and the session list itself may not have changed
        // at all, so the list has to hear about the link from here rather than from a sweep.
        project.messageBus.syncPublisher(com.canopy.services.CanopyTabsListener.TOPIC).tabsChanged()
        sessionService.refresh()
    }

    private fun stopLoading() {
        if (loadingStopped.compareAndSet(false, true)) {
            ApplicationManager.getApplication().invokeLater { loadingPanel.stopLoading() }
        }
    }

    private fun updateContextBar(status: ClaudeStatus?) {
        ApplicationManager.getApplication().invokeLater {
            val usedPercent = status?.contextUsedPercent
            if (usedPercent == null) {
                return@invokeLater
            }
            val pct = usedPercent.toInt().coerceIn(0, 100)
            contextProgress.value = pct
            contextProgress.string = "Context: $pct%"
            contextProgress.foreground = when {
                usedPercent < 50 -> COLOR_GREEN
                usedPercent < 80 -> COLOR_YELLOW
                else -> COLOR_RED
            }
            effortLabel.text = status.effortLevel?.let { "effort: $it" } ?: ""
            thinkingLabel.text = if (status.thinkingEnabled == true) "thinking" else ""
            remainingLabel.text = when {
                status.contextRemainingTokens != null && status.contextWindowSize != null ->
                    "${formatTokens(status.contextRemainingTokens)} / ${formatTokens(status.contextWindowSize)}"
                status.contextRemainingTokens != null ->
                    "${formatTokens(status.contextRemainingTokens)} left"
                else -> ""
            }
            costLabel.text = status.costUsd?.let { String.format("$%.2f", it) } ?: ""
            status.cliVersion?.let { running ->
                versionLinkTarget = running.replace('.', '-')
                val statusService = ClaudeStatusService.getInstance(project)
                statusService.refreshVersions()
                val installed = statusService.getInstalledCliVersion()
                val published = statusService.getPublishedCliVersion()

                val hasPendingUpgrade = installed != null && isNewerVersion(installed, running)
                val hasNewerPublished = published != null
                        && isNewerVersion(published, running)
                        && (installed == null || isNewerVersion(published, installed))

                val starSuffix = if (hasNewerPublished) " *" else ""
                versionLabel.text = if (hasPendingUpgrade) {
                    "v$running (update: v$installed)$starSuffix"
                } else {
                    "v$running$starSuffix"
                }
                versionLabel.foreground = if (hasPendingUpgrade) COLOR_YELLOW
                    else UIManager.getColor("Label.disabledForeground")

                val baseTip = "View release notes for v$running"
                versionLabel.toolTipText =
                    if (hasNewerPublished) "$baseTip — v$published published" else baseTip

                reconnectButton?.let {
                    if (hasPendingUpgrade) {
                        it.icon = com.intellij.icons.AllIcons.Actions.ForceRefresh
                        it.toolTipText = "Reconnect — update available (v$running \u2192 v$installed)"
                    } else {
                        it.icon = com.intellij.icons.AllIcons.Actions.Refresh
                        it.toolTipText = "Reconnect — close and resume this session"
                    }
                }
            }
            hasContextBarData = true
            refreshChromeVisibility()
        }
    }

    private fun restartSession() {
        try {
            ptyProcess?.destroyForcibly()
        } catch (_: Exception) {}
        ptyProcess = null

        com.canopy.services.SessionRuntimeService.getInstance(project).stop(file.sessionId ?: file.sessionKey)
        Disposer.dispose(sessionDisposable)
        sessionDisposable = Disposer.newDisposable(rootDisposable, "claude-session")

        focusComponent = null
        terminalWidget = null
        terminalArea = null
        maxEffortButton = null
        lastTitle = null

        hasContextBarData = false
        contextBar.isVisible = false
        contextProgress.value = 0
        contextProgress.string = "Context: —"
        effortLabel.text = ""
        thinkingLabel.text = ""
        remainingLabel.text = ""
        costLabel.text = ""
        versionLabel.text = ""
        versionLinkTarget = null

        currentContent?.let { loadingPanel.contentPanel.remove(it) }
        currentContent = null

        file.isUnresponsive = false
        refreshTabTitle()

        loadingStopped.set(false)
        loadingPanel.startLoading()
        loadingPanel.revalidate()
        loadingPanel.repaint()

        startTerminalSession()
        file.sessionId?.let { OpenSessionsPersistence.getInstance(project).add(it) }

        focusComponent?.let { fc ->
            ApplicationManager.getApplication().invokeLater {
                com.intellij.openapi.wm.IdeFocusManager.getInstance(project).requestFocus(fc, true)
            }
        }
    }

    private fun showAnswering(answering: Boolean) {
        file.isUnresponsive = !answering
        reconnectButton?.let {
            it.icon = if (answering) AllIcons.Actions.Refresh else AllIcons.General.Error
            it.toolTipText = if (answering) {
                "Reconnect — close and resume this session"
            } else {
                "Session unresponsive — click to reconnect"
            }
        }
        refreshTabTitle()
    }

    private fun refreshTabTitle(force: Boolean = false) {
        val signature = "${file.statusGlyph(project)} ${file.computeTabTitle()}"
        if (!force && signature == lastTitle) return
        lastTitle = signature
        ApplicationManager.getApplication().invokeLater {
            (FileEditorManager.getInstance(project) as? FileEditorManagerEx)
                ?.updateFilePresentation(file)
        }
    }

    override fun getComponent(): JComponent = loadingPanel
    override fun getPreferredFocusedComponent(): JComponent? = focusComponent
    override fun getName(): String = file.computeTabTitle()
    override fun isModified(): Boolean = false
    override fun getEmbeddedEditors(): List<com.intellij.openapi.editor.Editor> = emptyList()

    override fun isValid(): Boolean = true
    override fun setState(state: FileEditorState) {}
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun getFile(): VirtualFile = file
    override fun dispose() {
        isDisposed.set(true)
        Disposer.dispose(rootDisposable)
    }

    companion object {
        private val COLOR_GREEN = Color(0x5B, 0xA8, 0x5B)
        private val COLOR_YELLOW = Color(0xD4, 0xA0, 0x1E)
        private val COLOR_RED = Color(0xD4, 0x4B, 0x4B)

        private const val GIT_TIMEOUT_MS = 15_000L
        private const val GIT_OUTPUT_LIMIT = 400

        private const val TOOLBAR_REFRESH_FLOOR_MS = 5_000L

        private const val UPDATE_LABEL = "↓ Update"
        private const val MERGE_LABEL = "↑ Merge to project"
        private const val BUSY_SUFFIX = "…"

        private val GIT_FAILURE_HINTS: List<Pair<String, String>> = listOf(
            "not possible to fast-forward" to
                "The project branch has commits the worktree doesn't. Update the worktree first, then merge.",
            "would be overwritten by" to
                "There are uncommitted changes in the target directory. Commit or stash them first.",
            "cannot rebase: you have unstaged changes" to
                "The worktree has uncommitted changes. Commit or stash them first.",
            "please commit your changes or stash them" to
                "There are uncommitted changes in the target directory. Commit or stash them first.",
            "already a rebase-merge directory" to
                "A rebase is already in progress here — finish it with git rebase --continue or --abort.",
            "index.lock" to
                "Another git process is running in this repository. Wait for it to finish, or remove a stale index.lock.",
            "refusing to merge unrelated histories" to
                "These branches share no history, so git won't merge them.",
            "not something we can merge" to
                "That branch doesn't exist from the project's point of view.",
            "does not point to a valid object" to
                "The branch reference is broken or was deleted."
        )

        private fun formatTokens(tokens: Long): String = when {
            tokens >= 1_000_000 -> String.format("%.1fM", tokens / 1_000_000.0)
            tokens >= 1_000 -> String.format("%.0fk", tokens / 1_000.0)
            else -> tokens.toString()
        }

        private fun isNewerVersion(a: String, b: String): Boolean {
            val pa = a.split('.').map { it.toIntOrNull() ?: 0 }
            val pb = b.split('.').map { it.toIntOrNull() ?: 0 }
            val n = maxOf(pa.size, pb.size)
            for (i in 0 until n) {
                val ai = pa.getOrElse(i) { 0 }
                val bi = pb.getOrElse(i) { 0 }
                if (ai != bi) return ai > bi
            }
            return false
        }
    }
}
