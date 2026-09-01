package com.canopy.toolwindow

import com.canopy.model.SessionDisplay
import com.canopy.model.SessionStatus
import com.canopy.services.ClaudeSessionService
import com.canopy.util.ClaudePathEncoder
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SearchTextField
import com.intellij.ui.table.TableView
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseEvent
import java.nio.file.Files
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent
import javax.swing.table.TableCellRenderer

class SessionListPanel(
    private val project: Project,
    private val sessionService: ClaudeSessionService,
    private val getStatus: (String) -> SessionStatus,
    private val onSessionSelected: (SessionDisplay) -> Unit,
    private val onNewSession: (name: String) -> Unit,
    private val onForkSession: (SessionDisplay) -> Unit,
    private val worktreeMode: Boolean = false
) : Disposable {

    // Worktree mode only: git status for the Git column, swept in the background under the
    // polling gates in WorktreeStatusCache. Plain sessions all share the project dir, so the
    // same badge on every row would be noise. Assigned in init (it needs `this` as its
    // Disposable parent and the table model as its repaint target).
    private var worktreeStatus: WorktreeStatusCache? = null

    private val tableModel = SessionTableModel(
        getStatus,
        getDetail = ::sessionDetail,
        getAttention = ::sessionAttention,
        showWorktreeColumn = worktreeMode,
        getWorktreeStatus = if (worktreeMode) { name -> worktreeStatus?.get(name) } else null,
        hoveredRow = { hoveredRow }
    )
    private val table = object : TableView<SessionDisplay>(tableModel) {
        override fun prepareRenderer(renderer: TableCellRenderer, row: Int, column: Int): Component {
            val c = super.prepareRenderer(renderer, row, column)
            val session = tableModel.getItem(convertRowIndexToModel(row))
            // The Git column colours itself by git state (amber = uncommitted); the
            // session-status colouring below would flatten that back to the default.
            if (worktreeMode && convertColumnIndexToModel(column) == GIT_COLUMN_MODEL_INDEX) return c
            // The card carries its own tooltip; a table-level one competes with it and goes stale.
            if (!worktreeMode) return c
            when (getStatus(session.sessionId)) {
                SessionStatus.OPEN_EXTERNALLY -> {
                    c.foreground = if (isRowSelected(row)) selectionForeground
                                   else UIUtil.getLabelDisabledForeground()
                    toolTipText = "This session is open in an external terminal"
                }
                SessionStatus.OPEN_IN_PLUGIN -> {
                    c.foreground = if (isRowSelected(row)) selectionForeground else foreground
                    toolTipText = "This session is open \u2014 double-click to focus"
                }
                SessionStatus.AVAILABLE -> {
                    c.foreground = if (isRowSelected(row)) selectionForeground else foreground
                    toolTipText = null
                }
            }
            return c
        }
    }
    private val rootPanel = JPanel(BorderLayout())
    private val searchField = SearchTextField(false)
    private var transcriptMatches: Set<String> = emptySet()
    private var transcriptQuery = ""
    private var transcriptScan: java.util.concurrent.atomic.AtomicBoolean? = null
    private var transcriptScanning = ""
    private val moreRow = com.canopy.insight.MoreRow {
        limit += SESSION_PAGE
        applyFilter()
    }.apply {
        isOpaque = true
        background = com.canopy.insight.InsightUi.panelBackground()
    }
    private var limit = SESSION_PAGE

    private var allSessions: List<SessionDisplay> = emptyList()

    /** On-screen state and sweep floor for the orphaned-worktree scan (worktree mode only). */
    @Volatile private var panelVisible = false
    @Volatile private var lastOrphanSweep = 0L
    private val spinner = javax.swing.Timer(SPINNER_FRAME_MS.toInt()) { repaintWorkingRows() }
    private var spinningRows: List<Int> = emptyList()
    private var spinningFoundAt = 0L
    private val changeListener: () -> Unit = { reloadData() }

    // Orphaned-worktree section (worktree mode only): dirs under .claude/worktrees/ with no session.
    private val orphanListModel = javax.swing.DefaultListModel<OrphanWorktree>()
    private val orphanList = com.intellij.ui.components.JBList(orphanListModel)
    private val orphanPanel = JPanel(BorderLayout())
    private var orphanHeader: com.intellij.ui.components.JBLabel? = null
    private val orphanRefreshInFlight = java.util.concurrent.atomic.AtomicBoolean(false)
    private val tableScroll: JComponent = ScrollPaneFactory.createScrollPane(table).apply {
        border = JBUI.Borders.empty()
        viewport.isOpaque = false
        isOpaque = false
    }
    private val centerPanel = JPanel(BorderLayout())
    private val loadingPanel = com.intellij.ui.components.JBLoadingPanel(BorderLayout(), this).apply {
        setLoadingText("Reading transcripts")
        isOpaque = false
    }
    // Table on top, orphan list on bottom; user-draggable so orphans never crowd out sessions.
    private val orphanSplitter = com.intellij.ui.OnePixelSplitter(true, 0.7f)

    init {
        if (worktreeMode) {
            worktreeStatus = WorktreeStatusCache(project, this) {
                // Rows-updated, not data-changed: the latter clears the selection, and this
                // fires on a timer — losing the selected row every sweep would be maddening.
                if (tableModel.rowCount > 0) tableModel.fireTableRowsUpdated(0, tableModel.rowCount - 1)
            }.also { it.trackVisibility(rootPanel) }
            panelVisible = rootPanel.isShowing
            rootPanel.addHierarchyListener { e ->
                if (e.changeFlags and java.awt.event.HierarchyEvent.SHOWING_CHANGED.toLong() == 0L) return@addHierarchyListener
                val nowVisible = rootPanel.isShowing
                val became = nowVisible && !panelVisible
                panelVisible = nowVisible
                if (became) refreshOrphans(force = true)
            }
        }
        rootPanel.addHierarchyListener { event ->
            if (event.changeFlags and java.awt.event.HierarchyEvent.SHOWING_CHANGED.toLong() == 0L) {
                return@addHierarchyListener
            }
            if (rootPanel.isShowing) spinner.start() else spinner.stop()
        }
        spinner.isCoalesce = true

        setupTable()
        setupLayout()
        setupClickToOpen()
        setupContextMenu()
        sessionService.addChangeListener(changeListener)
        ApplicationManager.getApplication().invokeLater { reloadData() }
    }

    fun getComponent(): JComponent = rootPanel

    /** JTable has no hover state of its own, so the row under the cursor is tracked here. */
    private var hoveredRow: Int = -1

    /** The session the editor is showing, which survives the rows being replaced under it. */
    private var selectedSessionId: String? = null

    private fun trackHover() {
        val motion = object : java.awt.event.MouseAdapter() {
            override fun mouseMoved(event: java.awt.event.MouseEvent) = setHovered(table.rowAtPoint(event.point))

            override fun mouseExited(event: java.awt.event.MouseEvent) = setHovered(-1)
        }
        table.addMouseMotionListener(motion)
        table.addMouseListener(motion)
    }

    private fun setHovered(row: Int) {
        if (row == hoveredRow) return
        val previous = hoveredRow
        hoveredRow = row

        listOf(previous, row).filter { it >= 0 }.forEach { table.repaint(table.getCellRect(it, 0, true)) }
    }

    private fun setupTable() {
        trackHover()
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        table.setShowGrid(false)
        table.intercellSpacing = java.awt.Dimension(0, 0)
        table.rowHeight = if (worktreeMode) JBUI.scale(26) else JBUI.scale(46)
        // One column called "Name" is a header that says nothing; the worktree mode has real ones.
        if (!worktreeMode) table.tableHeader = null

        // 3-state click cycle: asc → desc → unsorted (= model order, which the service already
        // returns sortedByDescending { modified } so unsorted == MRU).
        val sorter = object : javax.swing.table.TableRowSorter<SessionTableModel>(tableModel) {
            override fun toggleSortOrder(column: Int) {
                val keys = sortKeys
                if (keys.size == 1 && keys[0].column == column &&
                    keys[0].sortOrder == javax.swing.SortOrder.DESCENDING) {
                    sortKeys = emptyList()
                } else {
                    super.toggleSortOrder(column)
                }
            }
        }
        if (worktreeMode) {
            sorter.setSortable(0, false)  // status icon column: inert
            sorter.setComparator(1, String.CASE_INSENSITIVE_ORDER)  // Name
        }
        if (worktreeMode) {
            sorter.setComparator(2, String.CASE_INSENSITIVE_ORDER)  // Worktree
            // Git (3) needs no comparator: its ColumnInfo declares Integer and ranks on
            // uncommitted-then-unmerged, so one click sorts the neglected worktrees to the top.
        }
        // The Git column needs no comparator: its ColumnInfo declares Integer
        // via getColumnClass(), so TableRowSorter uses natural Comparable ordering. (Declaring the
        // class is required — without it the default String class makes the sorter pick a Collator
        // that throws ClassCastException on the numeric values and silently aborts the sort.)
        table.rowSorter = sorter

        val cm = table.columnModel
        if (worktreeMode) {
            cm.getColumn(1).preferredWidth = 180  // Name
            cm.getColumn(2).preferredWidth = 140  // Worktree
            cm.getColumn(3).preferredWidth = 72   // Git
        }
    }

    private fun setupLayout() {
        val topBar = JPanel(BorderLayout(JBUI.scale(4), 0)).apply {
            border = JBUI.Borders.empty(2, 4)
            isOpaque = false
        }
        searchField.border = JBUI.Borders.empty()
        searchField.textEditor.border = JBUI.Borders.empty(2, 4)
        searchField.textEditor.emptyText.text = "Search names and transcripts"
        searchField.toolTipText = "Matches a session name, its branch or worktree, and anything said in it"
        rootPanel.isOpaque = false
        centerPanel.isOpaque = false
        table.isOpaque = false

        // A worktree has to be named before it can exist; a session names itself from what it is
        // asked, and can be renamed afterwards, so asking first only stands between + and a prompt.
        val newSessionAction = if (worktreeMode) {
            object : AnAction("New Worktree", "Start a new Claude session in a worktree", com.canopy.editor.ClaudeSessionIconProvider.TREE_ICON) {
                override fun actionPerformed(e: AnActionEvent) {
                    val dialog = WorktreeNameDialog(project)
                    if (!dialog.showAndGet()) return

                    dialog.enteredName?.takeIf { it.isNotEmpty() }?.let { onNewSession(it) }
                }
            }
        } else {
            object : AnAction("New Session", "Start a Claude session in this project", AllIcons.General.Add) {
                override fun actionPerformed(e: AnActionEvent) = onNewSession("")
            }
        }
        val refreshAction = object : AnAction("Refresh", "Refresh session list", AllIcons.Actions.Refresh) {
            override fun actionPerformed(e: AnActionEvent) {
                sessionService.refresh()
                // Explicit user action: skip the throttles so both update now.
                worktreeStatus?.requestRefresh(force = true)
                if (worktreeMode) refreshOrphans(force = true)
            }
        }
        val purgeAction = object : AnAction("Purge Old Sessions", "Delete sessions older than N days", AllIcons.Actions.GC) {
            override fun actionPerformed(e: AnActionEvent) {
                val dialog = PurgeDialog(project, allSessions, getStatus, sessionService)
                dialog.show()
            }
        }
        val toolbar = ActionManager.getInstance()
            .createActionToolbar("ClaudeSessionsToolbar", DefaultActionGroup(newSessionAction, refreshAction, purgeAction), true)
        toolbar.targetComponent = rootPanel

        searchField.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) { applyFilter() }
        })

        topBar.add(toolbar.component, BorderLayout.WEST)
        topBar.add(searchField, BorderLayout.CENTER)

        rootPanel.add(topBar, BorderLayout.NORTH)
        if (worktreeMode) {
            setupOrphanSection()
            centerPanel.add(loadingPanel, BorderLayout.CENTER)
            rootPanel.add(centerPanel, BorderLayout.CENTER)
        } else {
            rootPanel.add(loadingPanel, BorderLayout.CENTER)
        }
        loadingPanel.add(tableScroll, BorderLayout.CENTER)
        // The link belongs where the list runs out, not in a toolbar above rows it says nothing about.
        loadingPanel.add(moreRow, BorderLayout.SOUTH)
    }

    private fun setupOrphanSection() {
        orphanList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        orphanList.visibleRowCount = 4
        orphanList.cellRenderer = OrphanCardRenderer()
        orphanList.fixedCellHeight = JBUI.scale(46)

        val header = com.intellij.ui.components.JBLabel("Orphaned worktrees").apply {
            border = JBUI.Borders.empty(4, 6, 2, 6)
            foreground = UIUtil.getLabelDisabledForeground()
            toolTipText = "Worktrees with no Claude session, across the project and its submodules. " +
                "Right-click to start a session, delete, or reveal the directory."
        }
        orphanHeader = header

        orphanPanel.add(header, BorderLayout.NORTH)
        orphanPanel.add(ScrollPaneFactory.createScrollPane(orphanList), BorderLayout.CENTER)

        object : DoubleClickListener() {
            override fun onDoubleClick(event: MouseEvent): Boolean {
                val orphan = orphanList.selectedValue ?: return false
                startSessionInOrphan(orphan)
                return true
            }
        }.installOn(orphanList)

        installOrphanContextMenu()
    }

    private fun installOrphanContextMenu() {
        val group = DefaultActionGroup().apply {
            add(object : AnAction("New Session Here", "Start a Claude session in this worktree", com.canopy.editor.ClaudeSessionIconProvider.TREE_ICON) {
                override fun actionPerformed(e: AnActionEvent) {
                    orphanList.selectedValue?.let { startSessionInOrphan(it) }
                }
                override fun update(e: AnActionEvent) { e.presentation.isEnabled = orphanList.selectedValue != null }
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
            })
            add(object : AnAction("Open in File Manager", "Reveal the worktree directory", AllIcons.Actions.MenuOpen) {
                override fun actionPerformed(e: AnActionEvent) {
                    val orphan = orphanList.selectedValue ?: return
                    com.intellij.ide.actions.RevealFileAction.openDirectory(java.io.File(orphan.path))
                }
                override fun update(e: AnActionEvent) { e.presentation.isEnabled = orphanList.selectedValue != null }
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
            })
            add(object : AnAction("Show Changes", "Show this worktree's diff (committed vs base + uncommitted)", AllIcons.Actions.Diff) {
                override fun actionPerformed(e: AnActionEvent) {
                    val orphan = orphanList.selectedValue ?: return
                    showWorktreeChanges(orphan.path, "Changes — ${orphan.name}")
                }
                override fun update(e: AnActionEvent) { e.presentation.isEnabled = orphanList.selectedValue != null }
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
            })
            addSeparator()
            add(object : AnAction("Delete Worktree", "Remove this orphaned worktree", AllIcons.Actions.GC) {
                override fun actionPerformed(e: AnActionEvent) {
                    orphanList.selectedValue?.let { deleteOrphan(it) }
                }
                override fun update(e: AnActionEvent) { e.presentation.isEnabled = orphanList.selectedValue != null }
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
            })
        }
        PopupHandler.installPopupMenu(orphanList, group, "ClaudeOrphanWorktreeMenu")
    }

    /** A row is a session, and clicking one opens it: selecting without opening was never useful. */
    private fun setupClickToOpen() {
        table.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseReleased(event: MouseEvent) {
                if (event.isPopupTrigger || javax.swing.SwingUtilities.isRightMouseButton(event)) return
                val row = table.rowAtPoint(event.point)
                if (row < 0) return
                val session = tableModel.getItem(table.convertRowIndexToModel(row))
                if (getStatus(session.sessionId) == SessionStatus.OPEN_EXTERNALLY) return

                selectedSessionId = session.sessionId
                onSessionSelected(session)
            }
        })
    }

    /** The list is a map of what is open; the session on screen has to stay findable on it. */
    fun selectSession(sessionId: String?) {
        if (sessionId == null) return

        selectedSessionId = sessionId
        restoreSelection(scroll = true)
    }

    private fun restoreSelection(scroll: Boolean = false) {
        val sessionId = selectedSessionId ?: return
        val row = (0 until table.rowCount)
            .firstOrNull { tableModel.getItem(table.convertRowIndexToModel(it)).sessionId == sessionId }
        if (row == null) return
        if (table.selectedRow != row) table.selectionModel.setSelectionInterval(row, row)
        if (scroll) table.scrollRectToVisible(table.getCellRect(row, 0, true))
    }

    private fun setupContextMenu() {
        val group = DefaultActionGroup().apply {
            add(object : AnAction("Rename Session", "Give this session a name of your own", AllIcons.Actions.Edit) {
                override fun actionPerformed(e: AnActionEvent) {
                    val session = selectedSession() ?: return

                    com.canopy.editor.askInline(table, "Session name:", session.displayName) { name ->
                        com.canopy.editor.SessionInput.send(project, session.sessionId, "/rename ${'$'}name\r")
                    }
                }
                override fun update(e: AnActionEvent) {
                    val session = selectedSession()
                    e.presentation.isEnabled = session != null && com.canopy.editor.SessionInput.canSend(project, session.sessionId)
                }
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
            }.also {
                it.registerCustomShortcutSet(
                    com.intellij.openapi.actionSystem.CustomShortcutSet(
                        javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F2, 0)
                    ),
                    table
                )
            })
            add(object : AnAction("Fork Session", "Create a new session branching from this one", AllIcons.Actions.SplitHorizontally) {
                override fun actionPerformed(e: AnActionEvent) {
                    selectedSession()?.let { onForkSession(it) }
                }
                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = selectedSession() != null
                }
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
            })
            add(object : AnAction("Reply Without Opening", "Type an answer straight into a session that is waiting", AllIcons.Actions.Execute) {
                override fun actionPerformed(e: AnActionEvent) {
                    val session = selectedSession() ?: return
                    com.canopy.editor.SessionInput.promptAndReply(project, session.sessionId, session.displayName, table)
                }
                override fun update(e: AnActionEvent) {
                    val session = selectedSession()
                    e.presentation.isEnabled = session != null && com.canopy.editor.SessionInput.canSend(project, session.sessionId)
                }
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
            })
            add(object : AnAction("Open in Terminal", "Resume in the built-in Terminal tool window", AllIcons.Debugger.Console) {
                override fun actionPerformed(e: AnActionEvent) {
                    val session = selectedSession() ?: return
                    val log = com.intellij.openapi.diagnostic.Logger.getInstance("Canopy.SessionListPanel")
                    try {
                        val claudePath = com.canopy.settings.CanopySettings.getInstance().resolveClaudeBinary()
                        log.info("Canopy: Open in Terminal — claude=$claudePath, session=${session.sessionId}, name=${session.displayName}")

                        val terminal = org.jetbrains.plugins.terminal.TerminalToolWindowManager.getInstance(project)
                        val runner = org.jetbrains.plugins.terminal.LocalTerminalDirectRunner.createTerminalRunner(project)
                        val workDir = if (session.worktreeName != null && project.basePath != null) {
                            ClaudePathEncoder.worktreeAbsolutePath(project.basePath!!, session.worktreeName)
                        } else {
                            project.basePath ?: System.getProperty("user.home")
                        }
                        val claudeBin = claudePath ?: "claude"
                        log.info("Canopy: Open in Terminal — workDir=$workDir, command=[$claudeBin, --resume, ${session.sessionId}]")

                        val tabState = org.jetbrains.plugins.terminal.TerminalTabState().apply {
                            myTabName = "claude: ${session.displayName}"
                            myWorkingDirectory = workDir
                            myShellCommand = listOf(claudeBin, "--resume", session.sessionId)
                        }
                        terminal.createNewSession(runner, tabState)
                        log.info("Canopy: Open in Terminal — session created successfully")
                    } catch (ex: Exception) {
                        log.error("Canopy: Open in Terminal failed — session=${session.sessionId}", ex)
                    }
                }
                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = selectedSession() != null
                }
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
            })
            if (worktreeMode) {
                add(object : AnAction("Show Changes", "Show this worktree's diff (committed vs base + uncommitted)", AllIcons.Actions.Diff) {
                    override fun actionPerformed(e: AnActionEvent) {
                        val session = selectedSession() ?: return
                        val wt = session.worktreeName ?: return
                        val basePath = project.basePath ?: return
                        val dir = ClaudePathEncoder.worktreeAbsolutePath(basePath, wt)
                        showWorktreeChanges(dir, "Changes — $wt")
                    }
                    override fun update(e: AnActionEvent) {
                        e.presentation.isEnabled = selectedSession()?.worktreeName != null
                    }
                    override fun getActionUpdateThread() = ActionUpdateThread.EDT
                })
            }
            add(object : AnAction("Open Session Folder", "Reveal session files in the project directory", AllIcons.Actions.MenuOpen) {
                override fun actionPerformed(e: AnActionEvent) {
                    val basePath = project.basePath ?: return
                    val projectDir = ClaudePathEncoder.projectDir(basePath)
                    if (!Files.isDirectory(projectDir)) return
                    val vf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(projectDir)
                    if (vf != null) {
                        com.intellij.ide.actions.RevealFileAction.openDirectory(projectDir.toFile())
                    }
                }
                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = project.basePath != null
                }
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
            })
            add(object : AnAction("Copy Session ID", "Copy the session ID to clipboard", AllIcons.Actions.Copy) {
                override fun actionPerformed(e: AnActionEvent) {
                    selectedSession()?.let {
                        CopyPasteManager.getInstance().setContents(StringSelection(it.sessionId))
                    }
                }
                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = selectedSession() != null
                }
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
            })
            addSeparator()
            add(object : AnAction("Delete Session", "Permanently delete this session", AllIcons.Actions.GC) {
                override fun actionPerformed(e: AnActionEvent) {
                    val session = selectedSession() ?: return
                    val status = getStatus(session.sessionId)
                    if (status == SessionStatus.OPEN_IN_PLUGIN || status == SessionStatus.OPEN_EXTERNALLY) {
                        Messages.showWarningDialog(project, "Close the session before deleting it.", "Cannot Delete")
                        return
                    }
                    val wt = session.worktreeName
                    // In worktree mode, if this is the only session referencing the worktree, remove
                    // the worktree too rather than leaving it orphaned.
                    val lastForWorktree = worktreeMode && wt != null &&
                        allSessions.count { it.worktreeName == wt } <= 1
                    val message = if (lastForWorktree) {
                        "Delete session \"${session.displayName}\"?\n\n" +
                            "This is the only session in worktree \"$wt\", so the worktree will also be " +
                            "removed (git worktree remove).\n\nThis cannot be undone."
                    } else {
                        "Delete session \"${session.displayName}\"?\n\nThis cannot be undone."
                    }
                    val answer = Messages.showYesNoDialog(project, message, "Delete Session", Messages.getWarningIcon())
                    if (answer == Messages.YES) {
                        sessionService.deleteSession(session.sessionId)
                        if (lastForWorktree) deleteWorktreeAfterSession(wt!!)
                    }
                }
                override fun update(e: AnActionEvent) {
                    val session = selectedSession()
                    e.presentation.isEnabled = session != null &&
                        getStatus(session.sessionId) == SessionStatus.AVAILABLE
                }
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
            })
        }
        PopupHandler.installPopupMenu(table, group, "ClaudeSessionsContextMenu")
    }

    /**
     * The quiet half of a row: which repos the session wrote to, plus live context and spend for
     * the ones still running. Everything here is already in memory, so it costs no extra work.
     */
    private fun sessionDetail(session: SessionDisplay): String {
        val live = com.canopy.services.ClaudeStatusService.getInstance(project).getStatus(session.sessionId)
        val context = live?.contextUsedPercent?.let { "${it.toInt()}%" }
        val cost = live?.costUsd?.takeIf { it > 0 }?.let { String.format("$%.2f", it) }

        return listOfNotNull(context, cost).joinToString("  ")
    }

    private fun sessionAttention(session: SessionDisplay) = com.canopy.services.sessionAttentionIn(
        project,
        session,
        isRunning = sessionService.isExternallyOpen(session.sessionId) ||
            getStatus(session.sessionId) == SessionStatus.OPEN_IN_PLUGIN
    )

    /**
     * The selected session, or null when the selected row is only an open tab.
     *
     * A draft has no transcript, no id and nothing on disk, so everything that acts on a session -
     * fork, delete, resume, rename, reply - has nothing to act on and stays off.
     */
    private fun selectedSession(): SessionDisplay? = selectedRow()?.takeUnless { it.isDraft }

    private fun selectedRow(): SessionDisplay? {
        val row = table.selectedRow
        if (row < 0) return null
        return tableModel.getItem(table.convertRowIndexToModel(row))
    }

    /**
     * Discovery parses every transcript, so it must not run on the EDT. The spinner shows only
     * while the list is still empty; afterwards refreshes swap the rows underneath silently,
     * because a transcript write triggers this on every message.
     */
    /**
     * The draft row is keyed on its tab; the real row is keyed on the session.
     *
     * The moment a session starts, the draft stops being one and its row is replaced by the real
     * one - so a selection still pointing at the tab's key would find nothing and go blank. For a
     * session opened from the list the two keys are the same and this changes nothing.
     */
    private fun followLinkedDraft() {
        val key = selectedSessionId ?: return
        val linked = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).openFiles
            .filterIsInstance<com.canopy.editor.ClaudeSessionVirtualFile>()
            .firstOrNull { it.sessionKey == key }
            ?.sessionId ?: return

        selectedSessionId = linked
    }

    /**
     * A tab that is open and has not started yet, as a row.
     *
     * Worktree mode lists sessions belonging to a worktree, and a draft belongs to none until it
     * has run, so it only ever appears in the plain list.
     */
    private fun openDrafts(): List<SessionDisplay> {
        if (worktreeMode) return emptyList()

        val open = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).openFiles
            .filterIsInstance<com.canopy.editor.ClaudeSessionVirtualFile>()
            .filter { it.sessionId == null && !it.isShellSession }
            .map { DraftSource(it.sessionKey, it.baseName) }

        return draftRows(open, project.basePath.orEmpty(), System.currentTimeMillis())
    }

    private fun reloadData() {
        if (allSessions.isEmpty()) loadingPanel.startLoading()

        com.canopy.util.CanopyExecutor.submit {
            val discovered = sessionService.getSessions().filter {
                if (worktreeMode) it.worktreeName != null else it.worktreeName == null
            }
            ApplicationManager.getApplication().invokeLater { applyDiscovered(discovered) }
        }
    }

    private fun applyDiscovered(discovered: List<SessionDisplay>) {
        com.canopy.services.RepoScopeService.getInstance(project).refreshIfStale { table.repaint() }
        loadingPanel.stopLoading()
        // Most recent by default: a row that moves as its agent changes state means the list
        // reorders itself under the pointer, and the glyph already says what is blocked.
        val sorted = if (com.canopy.settings.CanopySettings.getInstance().state.sortAttentionFirst) {
            discovered.sortedWith(compareBy<SessionDisplay> { sessionAttention(it).rank }.thenByDescending { it.modified })
        } else {
            discovered.sortedByDescending { it.lastPromptAt ?: it.modified }
        }
        // Always first: a session you have just started is the one you are about to type into.
        allSessions = openDrafts() + sorted
        followLinkedDraft()
        applyFilter()
        if (worktreeMode) {
            refreshOrphans()
            // Throttled + visibility-gated inside the cache: reloadData fires on every
            // transcript write, which is continuous while a session is working.
            worktreeStatus?.apply {
                setTargets(allSessions.mapNotNull { it.worktreeName })
                requestRefresh()
            }
        }
    }

    /** Drives the search field from outside, so the Repos tree can narrow this list to one worktree. */
    fun filterByWorktree(worktreeName: String?) {
        searchField.text = worktreeName.orEmpty()
    }

    /**
     * A session is named after its first prompt, so a name match finds almost nothing of what a
     * session actually contains. The transcripts are scanned off the thread and the rows they
     * match are folded in when the scan lands, leaving the name match to render immediately.
     */
    private fun searchTranscripts(query: String) {
        if (query.length < MIN_TRANSCRIPT_QUERY) {
            transcriptScan?.set(true)
            transcriptScan = null
            transcriptScanning = ""
            transcriptMatches = emptySet()
            transcriptQuery = ""
            return
        }

        // A reload repaints the list while a scan is still running, and restarting the scan there
        // would mean it never finishes for as long as an agent keeps writing.
        if (query == transcriptQuery || query == transcriptScanning) return

        val basePath = project.basePath ?: return
        transcriptScan?.set(true)
        val cancelled = java.util.concurrent.atomic.AtomicBoolean(false)
        transcriptScan = cancelled
        transcriptScanning = query
        val days = com.canopy.settings.CanopySettings.getInstance().state.recentSessionDays

        com.canopy.util.CanopyExecutor.submit {
            val found = com.canopy.services.TranscriptSearch.sessionsContaining(basePath, query, days, cancelled)
            if (cancelled.get()) return@submit

            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                if (cancelled.get() || project.isDisposed) return@invokeLater

                transcriptScanning = ""
                transcriptQuery = query
                transcriptMatches = found
                applyFilter()
            }
        }
    }

    /**
     * Old sessions are kept out of the way rather than deleted: the list grows for months and a
     * search across all of it still has to work, so a query lifts the cut-off on its own.
     */
    private fun applyFilter() {
        val query = searchField.text.orEmpty().lowercase().trim()
        searchTranscripts(query)
        val visible = allSessions
        val filtered = if (query.isEmpty()) {
            visible
        } else {
            visible.filter { session ->
                session.sessionId in transcriptMatches ||
                    session.displayName.lowercase().contains(query) ||
                    session.firstPrompt.lowercase().contains(query) ||
                    session.gitBranch?.lowercase()?.contains(query) == true ||
                    session.worktreeName?.lowercase()?.contains(query) == true
            }
        }
        val page = com.canopy.insight.pageful(filtered, limit)
        moreRow.show(page.remaining, SESSION_PAGE)
        tableModel.items = page.shown
        // Replacing the rows drops the selection, and the rows are replaced whenever an agent
        // writes, so the session being reviewed has to be put back on every reload.
        restoreSelection()
    }


    /**
     * Only the rows that are spinning repaint, and only while the list is on screen, so an idle
     * project pays nothing for the animation.
     */
    /**
     * Repaints the glyph of whatever is spinning, and costs nothing while nothing is.
     *
     * This used to work out which rows were spinning on every frame: forty rows through the
     * attention rules nine times a second, on the EDT, whether or not any agent was running. The
     * answer only changes when the data does, so it is worked out a couple of times a second, and
     * the timer drops to that rate whenever the answer is none.
     */
    private fun repaintWorkingRows() {
        if (!table.isShowing) return

        val now = System.currentTimeMillis()
        if (now - spinningFoundAt >= SPIN_RESCAN_MS) {
            spinningFoundAt = now
            spinningRows = (0 until tableModel.rowCount).filter { row ->
                val attention = sessionAttention(tableModel.getItem(row))
                attention == com.canopy.model.SessionAttention.Working ||
                    attention == com.canopy.model.SessionAttention.Compacting
            }
            spinner.delay = if (spinningRows.isEmpty()) SPIN_RESCAN_MS.toInt() else SPINNER_FRAME_MS.toInt()
        }
        if (spinningRows.isEmpty()) return

        // Only the strip the glyph occupies: the rest of the card has not changed.
        for (row in spinningRows) {
            if (row >= table.rowCount) continue
            val cell = table.getCellRect(row, 0, true)
            table.repaint(cell.x, cell.y, GLYPH_STRIP_WIDTH, cell.height)
        }
    }

    /** Opening or closing a tab adds or removes a draft row, which is a different list, not a repaint. */
    fun refreshRows() = reloadData()

    /**
     * Recompute the orphaned-worktree list off the EDT (dir scan + `git worktree list`).
     *
     * Same gating as the Git column: reloadData() fires on every transcript write, so being
     * single-flight isn't enough on its own — this also needs to stay off while the tab is
     * hidden and to coalesce bursts. [force] is for explicit user actions and for the sweeps
     * that must follow a delete.
     */
    private fun refreshOrphans(force: Boolean = false) {
        val basePath = project.basePath ?: return
        if (!force) {
            if (!panelVisible) return
            val now = System.currentTimeMillis()
            if (now - lastOrphanSweep < ORPHAN_SWEEP_MIN_MS) return
        }
        // Stamp only once a sweep actually starts, so a call rejected by the single-flight
        // guard doesn't push the floor out and delay the next real one.
        if (!orphanRefreshInFlight.compareAndSet(false, true)) return
        lastOrphanSweep = System.currentTimeMillis()
        com.canopy.util.CanopyExecutor.submit {
            try {
                val sessionNames = sessionService.getSessions().mapNotNull { it.worktreeName }.toSet()
                val scopeService = com.canopy.services.RepoScopeService.getInstance(project)
                scopeService.refreshIfStale { refreshOrphans(force = true) }
                val orphans = WorktreeInspector.findOrphans(scopeService.cachedScopes(), sessionNames)
                ApplicationManager.getApplication().invokeLater {
                    orphanListModel.clear()
                    orphans.forEach { orphanListModel.addElement(it) }
                    updateOrphanVisibility()
                }
            } finally {
                orphanRefreshInFlight.set(false)
            }
        }
    }

    private fun updateOrphanVisibility() {
        val n = orphanListModel.size()
        orphanHeader?.text = "Orphaned worktrees ($n)"
        if (n > 0 && orphanSplitter.parent == null) {
            centerPanel.remove(tableScroll)
            orphanSplitter.firstComponent = tableScroll
            orphanSplitter.secondComponent = orphanPanel
            centerPanel.add(orphanSplitter, BorderLayout.CENTER)
        } else if (n == 0 && orphanSplitter.parent != null) {
            centerPanel.remove(orphanSplitter)
            orphanSplitter.firstComponent = null
            orphanSplitter.secondComponent = null
            centerPanel.add(tableScroll, BorderLayout.CENTER)
        }
        centerPanel.revalidate()
        centerPanel.repaint()
    }

    private fun startSessionInOrphan(orphan: OrphanWorktree) {
        // Optimistically drop it — creating a session makes it non-orphan; a later reload re-adds
        // it if the session never materializes.
        orphanListModel.removeElement(orphan)
        updateOrphanVisibility()
        onNewSession(orphan.name)
    }

    private fun deleteOrphan(orphan: OrphanWorktree) {
        val basePath = project.basePath ?: return
        if (orphan.registered) {
            val branchLine = orphan.branch?.let { "\nand deletes branch \"$it\" if it is fully merged." } ?: "."
            val answer = Messages.showYesNoDialog(
                project,
                "Delete worktree \"${orphan.name}\"?\n\n" +
                    "Runs git worktree remove on ${orphan.path}$branchLine\n\nThis cannot be undone.",
                "Delete Worktree",
                Messages.getWarningIcon()
            )
            if (answer == Messages.YES) runOrphanRemoval(orphan.repoRoot, orphan, force = false)
        } else {
            val answer = Messages.showYesNoDialog(
                project,
                "\"${orphan.name}\" is not a registered git worktree.\n\n" +
                    "Delete the directory ${orphan.path}?\n\nThis cannot be undone.",
                "Delete Directory",
                Messages.getWarningIcon()
            )
            if (answer != Messages.YES) return
            com.canopy.util.CanopyExecutor.submit {
                val (ok, msg) = WorktreeInspector.deleteDirectory(orphan.path)
                ApplicationManager.getApplication().invokeLater {
                    if (!ok) Messages.showErrorDialog(project, "Failed to delete directory:\n$msg", "Delete Failed")
                    refreshVfs(orphan.path)
                    refreshOrphans(force = true)
                }
            }
        }
    }

    private fun runOrphanRemoval(basePath: String, orphan: OrphanWorktree, force: Boolean) {
        com.canopy.util.CanopyExecutor.submit {
            val (ok, output) = WorktreeInspector.remove(basePath, orphan.path, force)
            if (!ok && !force) {
                ApplicationManager.getApplication().invokeLater {
                    val retry = Messages.showYesNoDialog(
                        project,
                        "git worktree remove failed:\n\n${output.take(400)}\n\n" +
                            "Force delete? This discards any uncommitted changes in the worktree.",
                        "Force Delete Worktree",
                        "Force Delete", "Cancel",
                        Messages.getWarningIcon()
                    )
                    if (retry == Messages.YES) runOrphanRemoval(orphan.repoRoot, orphan, force = true)
                }
                return@submit
            }
            if (!ok) {
                ApplicationManager.getApplication().invokeLater {
                    Messages.showErrorDialog(project, "Failed to remove worktree:\n${output.take(400)}", "Delete Failed")
                    refreshOrphans(force = true)
                }
                return@submit
            }
            val branchNote = orphan.branch?.let { branch ->
                val (deleted, _) = WorktreeInspector.deleteBranchIfMerged(basePath, branch)
                if (deleted) null else "Kept branch \"$branch\" — it has commits not merged into the base branch."
            }
            ApplicationManager.getApplication().invokeLater {
                if (branchNote != null) Messages.showInfoMessage(project, branchNote, "Worktree Deleted")
                refreshVfs(orphan.path)
                refreshOrphans(force = true)
            }
        }
    }

    /** Removes the worktree for [worktreeName] after its last session was deleted (no orphan left behind). */
    private fun deleteWorktreeAfterSession(worktreeName: String) {
        val basePath = project.basePath ?: return
        com.canopy.util.CanopyExecutor.submit {
            val scopes = com.canopy.services.RepoScopeService.getInstance(project).cachedScopes()
            val orphan = WorktreeInspector.findOrphans(scopes, emptySet()).firstOrNull { it.name == worktreeName }
                ?: OrphanWorktree(
                    name = worktreeName,
                    path = ClaudePathEncoder.worktreeAbsolutePath(basePath, worktreeName),
                    branch = null,
                    reason = com.canopy.model.OrphanReason.EmptyDirectory,
                    repoLabel = "",
                    repoRoot = basePath
                )
            ApplicationManager.getApplication().invokeLater {
                if (orphan.registered) {
                    runOrphanRemoval(orphan.repoRoot, orphan, force = false)
                } else if (Files.exists(java.nio.file.Path.of(orphan.path))) {
                    com.canopy.util.CanopyExecutor.submit {
                        val (ok, msg) = WorktreeInspector.deleteDirectory(orphan.path)
                        ApplicationManager.getApplication().invokeLater {
                            if (!ok) Messages.showErrorDialog(project, "Failed to delete directory:\n$msg", "Delete Failed")
                            refreshVfs(orphan.path)
                            refreshOrphans(force = true)
                        }
                    }
                }
            }
        }
    }

    /** Compute a worktree's diff off the EDT, then open IntelliJ's native diff viewer. */
    private fun showWorktreeChanges(worktreeDir: String, title: String) {
        com.canopy.util.CanopyExecutor.submit {
            val result = WorktreeChanges.compute(worktreeDir)
            ApplicationManager.getApplication().invokeLater {
                if (result.changes.isEmpty()) {
                    Messages.showInfoMessage(project, result.error ?: "No changes in this worktree.", "Worktree Changes")
                } else {
                    WorktreeChanges.openDialog(project, title, worktreeDir, result.changes)
                }
            }
        }
    }

    /** Tell the IDE's VFS that a directory changed on disk so the Project view / editors refresh. */
    private fun refreshVfs(path: String) {
        val parent = java.nio.file.Path.of(path).parent ?: return
        val vf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(parent) ?: return
        com.intellij.openapi.vfs.VfsUtil.markDirtyAndRefresh(true, true, true, vf)
    }

    override fun dispose() {
        sessionService.removeChangeListener(changeListener)
    }

    private companion object {
        /** Model index of the Git column in worktree mode: status, Name, Worktree, Git, … */
        const val GIT_COLUMN_MODEL_INDEX = 3

        /** Floor between orphaned-worktree scans driven by session-list changes. */
        const val ORPHAN_SWEEP_MIN_MS = 15_000L
    }

}

private class PurgeDialog(
    project: Project,
    private val sessions: List<SessionDisplay>,
    private val getStatus: (String) -> SessionStatus,
    private val sessionService: ClaudeSessionService
) : com.intellij.openapi.ui.DialogWrapper(project) {

    private val daysField = javax.swing.JTextField("30", 6)
    private val countLabel = com.intellij.ui.components.JBLabel(" ")
    private var candidateCount = 0

    init {
        title = "Purge Old Sessions"
        setOKButtonText("Delete")
        updateCount()
        daysField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) { updateCount() }
        })
        init()
    }

    private fun updateCount() {
        val days = daysField.text.trim().toLongOrNull()
        if (days == null || days < 1) {
            countLabel.text = " "
            candidateCount = 0
            return
        }
        val cutoff = Instant.now().minus(days, ChronoUnit.DAYS)
        candidateCount = sessions.count {
            it.modified.isBefore(cutoff) && getStatus(it.sessionId) == SessionStatus.AVAILABLE
        }
        countLabel.text = if (candidateCount == 0) "No sessions to delete"
            else "$candidateCount session${if (candidateCount > 1) "s" else ""} will be deleted"
    }

    override fun createCenterPanel(): JComponent {
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(com.intellij.ui.components.JBLabel("Delete sessions not modified in the last N days:"))
            add(javax.swing.Box.createVerticalStrut(JBUI.scale(6)))
            add(daysField)
            add(javax.swing.Box.createVerticalStrut(JBUI.scale(4)))
            add(countLabel)
        }
    }

    override fun getPreferredFocusedComponent(): JComponent = daysField

    override fun doOKAction() {
        val days = daysField.text.trim().toLongOrNull() ?: return
        if (days < 1 || candidateCount == 0) return
        val cutoff = Instant.now().minus(days, ChronoUnit.DAYS)
        val candidates = sessions.filter {
            it.modified.isBefore(cutoff) && getStatus(it.sessionId) == SessionStatus.AVAILABLE
        }
        for (session in candidates) {
            sessionService.deleteSession(session.sessionId)
        }
        super.doOKAction()
    }
}

private const val MIN_TRANSCRIPT_QUERY = 2

private const val SESSION_PAGE = 30

/** How often the spinning rows are worked out again, and the timer's rate while there are none. */
private const val SPIN_RESCAN_MS = 500L

private val GLYPH_STRIP_WIDTH = JBUI.scale(44)
