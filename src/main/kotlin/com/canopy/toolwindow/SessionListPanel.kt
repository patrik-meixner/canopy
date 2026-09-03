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

    private var worktreeStatus: WorktreeStatusCache? = null

    private val tableModel = SessionTableModel(
        getStatus,
        getDetail = ::sessionDetail,
        getAttention = ::sessionAttention,
        showWorktreeColumn = worktreeMode,
        getWorktreeStatus = if (worktreeMode) { name -> worktreeStatus?.get(name) } else null,
        hoveredRow = { hoveredRow },
        isCloseHovered = { closeHovered }
    )
    private val table = object : TableView<SessionDisplay>(tableModel) {
        /**
         * A renderer draws into one shared component that lives in no hierarchy, and giving that
         * component a tooltip left the tooltip window painting the card at the component's stale
         * bounds: a stray copy of a row, next to the tip, over whatever was there.
         */
        override fun getToolTipText(event: MouseEvent): String? {
            if (worktreeMode) return super.getToolTipText(event)

            val row = rowAtPoint(event.point)
            if (row < 0) return null
            if (closeHitArea(getCellRect(row, 0, true)).contains(event.point)) return "Stop this session"

            return tooltipFor(tableModel.getItem(convertRowIndexToModel(row))).takeIf { it.isNotBlank() }
        }

        override fun prepareRenderer(renderer: TableCellRenderer, row: Int, column: Int): Component {
            val c = super.prepareRenderer(renderer, row, column)
            val session = tableModel.getItem(convertRowIndexToModel(row))
            if (worktreeMode && convertColumnIndexToModel(column) == GIT_COLUMN_MODEL_INDEX) return c
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

    @Volatile private var panelVisible = false
    @Volatile private var lastOrphanSweep = 0L
    private val spinner = javax.swing.Timer(SPINNER_FRAME_MS.toInt()) { repaintWorkingRows() }
    private val reloadAlarm = com.intellij.util.Alarm(com.intellij.util.Alarm.ThreadToUse.SWING_THREAD, this)
    private var spinningRows: List<Int> = emptyList()
    private var spinningFoundAt = 0L
    private val changeListener: () -> Unit = { reloadData() }

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

    private var hoveredRow: Int = -1
    private var closeHovered = false

    private var selectedSessionId: String? = null

    private fun trackHover() {
        val motion = object : java.awt.event.MouseAdapter() {
            override fun mouseMoved(event: java.awt.event.MouseEvent) {
                setHovered(table.rowAtPoint(event.point))
                setCloseHovered(isOverClose(event))
            }

            override fun mouseExited(event: java.awt.event.MouseEvent) {
                setCloseHovered(false)
                setHovered(-1)
            }
        }
        table.addMouseMotionListener(motion)
        table.addMouseListener(motion)
    }

    private fun isOverClose(event: MouseEvent): Boolean {
        if (worktreeMode) return false

        val row = table.rowAtPoint(event.point)
        if (row < 0) return false
        val session = tableModel.getItem(table.convertRowIndexToModel(row))
        val hasSomethingToStop = session.isTerminal || getStatus(session.sessionId) == SessionStatus.OPEN_IN_PLUGIN
        if (!hasSomethingToStop) return false

        return closeHitArea(table.getCellRect(row, 0, true)).contains(event.point)
    }

    private fun setCloseHovered(over: Boolean) {
        if (over == closeHovered) return

        closeHovered = over
        if (hoveredRow >= 0) table.repaint(table.getCellRect(hoveredRow, 0, true))
    }

    private fun setHovered(row: Int) {
        if (row == hoveredRow) return
        val previous = hoveredRow
        hoveredRow = row

        listOf(previous, row).filter { it >= 0 }.forEach { table.repaint(table.getCellRect(it, 0, true)) }
    }

    private fun setupTable() {
        trackHover()
        // A card wider than its column makes JBTable pop the overflowing tail into a little window
        // of its own, just outside the list: a floating copy of half a row, cross and all.
        table.setExpandableItemsEnabled(false)
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        table.setShowGrid(false)
        table.intercellSpacing = java.awt.Dimension(0, 0)
        table.rowHeight = if (worktreeMode) JBUI.scale(26) else CARD_ROW_HEIGHT
        if (!worktreeMode) table.tableHeader = null

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

    private fun setupClickToOpen() {
        table.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseReleased(event: MouseEvent) {
                if (event.isPopupTrigger || javax.swing.SwingUtilities.isRightMouseButton(event)) return
                val row = table.rowAtPoint(event.point)
                if (row < 0) return
                val session = tableModel.getItem(table.convertRowIndexToModel(row))
                if (isOverClose(event)) {
                    return if (session.isTerminal) stopTerminal(session.sessionId) else stopSession(session.sessionId)
                }
                selectedSessionId = session.sessionId
                if (session.isTerminal) return focusTerminal(session.sessionId, event.isMetaDown)
                if (getStatus(session.sessionId) == SessionStatus.OPEN_EXTERNALLY) return

                activate(session, isAdditive = event.isMetaDown)
                if (session.isDraft) return

                onSessionSelected(session)
            }
        })
    }

    fun selectSession(sessionId: String?) {
        if (sessionId == null || com.canopy.services.SessionStaging.isRearranging(project)) return

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
            add(object : AnAction("Rename", "Give this session a name of your own", AllIcons.Actions.Edit) {
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
            add(object : AnAction("Add to Stage", "Show this session beside the ones already open", AllIcons.General.Add) {
                override fun actionPerformed(e: AnActionEvent) {
                    selectedSession()?.let { activate(it, isAdditive = true) }
                }
                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = selectedSession() != null
                }
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
            })
            add(object : AnAction("Stop", "End the agent; closing its tab only hides it", AllIcons.Actions.Suspend) {
                override fun actionPerformed(e: AnActionEvent) {
                    val row = selectedRow()?.takeUnless { it.isTerminal } ?: return
                    stopSession(row.sessionId)
                }
                override fun update(e: AnActionEvent) {
                    val row = selectedRow()
                    e.presentation.isEnabled = row != null && !row.isTerminal && isRunning(row.sessionId)
                }
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
            })
            addSeparator()
            add(object : AnAction("New Terminal", "Open a shell that belongs to this session", AllIcons.Debugger.Console) {
                override fun actionPerformed(e: AnActionEvent) {
                    val row = selectedRow()?.takeUnless { it.isTerminal } ?: return
                    if (sessionFileFor(row.sessionId) == null) openClaudeSession(project, row)

                    com.canopy.editor.openTerminalFor(project, sessionFileFor(row.sessionId))
                }
                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = selectedRow()?.isTerminal == false
                }
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
            })
            add(object : AnAction("Fork", "Create a new session branching from this one", AllIcons.Actions.SplitHorizontally) {
                override fun actionPerformed(e: AnActionEvent) {
                    selectedSession()?.let { onForkSession(it) }
                }
                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = selectedSession() != null
                }
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
            })
            add(object : AnAction("Reply", "Type an answer straight into a session that is waiting", AllIcons.Actions.Execute) {
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
            addSeparator()
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
            add(object : AnAction("Open Folder", "Reveal session files in the project directory", AllIcons.Actions.MenuOpen) {
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
            add(object : AnAction("Copy ID", "Copy the session ID to clipboard", AllIcons.Actions.Copy) {
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
            add(object : AnAction("Delete", "Permanently delete this session", AllIcons.Actions.GC) {
                override fun actionPerformed(e: AnActionEvent) {
                    val session = selectedSession() ?: return
                    val status = getStatus(session.sessionId)
                    if (status == SessionStatus.OPEN_IN_PLUGIN || status == SessionStatus.OPEN_EXTERNALLY) {
                        Messages.showWarningDialog(project, "Close the session before deleting it.", "Cannot Delete")
                        return
                    }
                    val wt = session.worktreeName
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

    private fun selectedSession(): SessionDisplay? = selectedRow()?.takeUnless { it.isDraft || it.isTerminal }

    private fun selectedRow(): SessionDisplay? {
        val row = table.selectedRow
        if (row < 0) return null
        return tableModel.getItem(table.convertRowIndexToModel(row))
    }

    private fun followLinkedDraft() {
        val key = selectedSessionId ?: return
        val linked = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).openFiles
            .filterIsInstance<com.canopy.editor.ClaudeSessionVirtualFile>()
            .firstOrNull { it.sessionKey == key }
            ?.sessionId ?: return

        selectedSessionId = linked
    }

    private fun openDrafts(): List<SessionDisplay> {
        if (worktreeMode) return emptyList()

        val open = canopyFiles()
            .filter { it.sessionId == null && !it.isShellSession }
            .map { DraftSource(it.sessionKey, it.baseName) }

        return draftRows(open, project.basePath.orEmpty(), System.currentTimeMillis())
    }

    private fun openTerminals(): List<TerminalSource> {
        if (worktreeMode) return emptyList()

        val open = canopyFiles()
        val startedByKey = open.mapNotNull { file -> file.sessionId?.let { file.sessionKey to it } }.toMap()

        return open.filter { it.isShellSession }.mapNotNull { shell ->
            val ownerKey = shell.ownerSessionKey ?: return@mapNotNull null
            TerminalSource(
                shell.sessionKey,
                com.canopy.editor.terminalLabel(project, shell),
                startedByKey[ownerKey] ?: ownerKey
            )
        }
    }

    private fun canopyFiles(): List<com.canopy.editor.ClaudeSessionVirtualFile> =
        com.canopy.services.SessionRuntimeService.getInstance(project).all().map { it.file }

    private fun sessionFileFor(rowId: String): com.canopy.editor.ClaudeSessionVirtualFile? =
        com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).openFiles
            .filterIsInstance<com.canopy.editor.ClaudeSessionVirtualFile>()
            .firstOrNull { !it.isShellSession && (it.sessionId == rowId || it.sessionKey == rowId) }

    private fun isRunning(rowId: String): Boolean =
        com.canopy.services.SessionRuntimeService.getInstance(project).existing(rowId) != null ||
            sessionFileFor(rowId) != null

    private fun stopTerminal(key: String) {
        val runtimes = com.canopy.services.SessionRuntimeService.getInstance(project)
        val shell = canopyFiles().firstOrNull { it.sessionKey == key } ?: return

        com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).closeFile(shell)
        runtimes.stop(key)
        reloadData()
    }

    private fun stopSession(rowId: String) {
        val runtimes = com.canopy.services.SessionRuntimeService.getInstance(project)
        val manager = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project)
        val session = sessionFileFor(rowId)
        val shells = canopyFiles().filter { it.isShellSession && it.ownerSessionKey == session?.sessionKey }

        (shells + listOfNotNull(session)).forEach { file ->
            manager.closeFile(file)
            runtimes.keyOf(file)?.let(runtimes::stop)
        }
        runtimes.stop(rowId)
        com.canopy.services.OpenSessionsPersistence.getInstance(project).remove(rowId)
        reloadData()
    }

    private fun activate(session: SessionDisplay, isAdditive: Boolean) {
        val target = canopyFiles().firstOrNull {
            !it.isShellSession && (it.sessionId == session.sessionId || it.sessionKey == session.sessionId)
        }

        stageTo(target?.sessionKey ?: session.sessionId, isAdditive)
        if (target == null) return

        com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).openFile(target, true)
    }

    private fun stageTo(targetKey: String, isAdditive: Boolean) {
        val manager = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project)
        val known = canopyFiles()
        val openKeys = manager.openFiles.filterIsInstance<com.canopy.editor.ClaudeSessionVirtualFile>()
            .map { it.sessionKey }
            .toSet()
        val tabs = known.map { StageTab(it.sessionKey, it.isShellSession, it.ownerSessionKey, it.sessionKey in openKeys) }
        val byKey = known.associateBy { it.sessionKey }
        val plan = stagePlan(tabs, targetKey, isAdditive)

        com.canopy.services.SessionStaging.getInstance(project).rearranging {
            plan.close.mapNotNull(byKey::get).forEach(manager::closeFile)
            plan.open.mapNotNull(byKey::get).forEach { manager.openFile(it, false) }
        }
    }

    private fun focusTerminal(key: String, isAdditive: Boolean) {
        val manager = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project)
        val shell = canopyFiles().firstOrNull { it.sessionKey == key } ?: return

        shell.ownerSessionKey?.let { stageTo(it, isAdditive) }
        manager.openFile(shell, true)
    }

    private fun reloadData() {
        if (reloadAlarm.isDisposed) return

        reloadAlarm.cancelAllRequests()
        reloadAlarm.addRequest(::reloadNow, RELOAD_COALESCE_MS)
    }

    private fun reloadNow() {
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
        val sorted = if (com.canopy.settings.CanopySettings.getInstance().state.sortAttentionFirst) {
            discovered.sortedWith(compareBy<SessionDisplay> { sessionAttention(it).rank }.thenByDescending { it.modified })
        } else {
            discovered.sortedByDescending { it.lastPromptAt ?: it.modified }
        }
        allSessions = openDrafts() + sorted
        followLinkedDraft()
        applyFilter()
        if (worktreeMode) {
            refreshOrphans()
            worktreeStatus?.apply {
                setTargets(allSessions.mapNotNull { it.worktreeName })
                requestRefresh()
            }
        }
    }

    fun filterByWorktree(worktreeName: String?) {
        searchField.text = worktreeName.orEmpty()
    }

    private fun searchTranscripts(query: String) {
        if (query.length < MIN_TRANSCRIPT_QUERY) {
            transcriptScan?.set(true)
            transcriptScan = null
            transcriptScanning = ""
            transcriptMatches = emptySet()
            transcriptQuery = ""
            return
        }

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
        tableModel.items = withTerminalRows(
            page.shown, openTerminals(), project.basePath.orEmpty(), System.currentTimeMillis()
        )
        applyRowHeights()
        restoreSelection()
    }

    private fun applyRowHeights() {
        if (worktreeMode) return

        (0 until table.rowCount).forEach { row ->
            val isTerminal = tableModel.getItem(table.convertRowIndexToModel(row)).isTerminal
            val height = if (isTerminal) TerminalCardRenderer.ROW_HEIGHT else CARD_ROW_HEIGHT
            if (table.getRowHeight(row) != height) table.setRowHeight(row, height)
        }
    }

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

        for (row in spinningRows) {
            if (row >= table.rowCount) continue
            val cell = table.getCellRect(row, 0, true)
            table.repaint(cell.x, cell.y, GLYPH_STRIP_WIDTH, cell.height)
        }
    }

    fun refreshRows() = reloadData()

    fun refreshFromTabs() = applyFilter()

    private fun refreshOrphans(force: Boolean = false) {
        val basePath = project.basePath ?: return
        if (!force) {
            if (!panelVisible) return
            val now = System.currentTimeMillis()
            if (now - lastOrphanSweep < ORPHAN_SWEEP_MIN_MS) return
        }
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

    private fun refreshVfs(path: String) {
        val parent = java.nio.file.Path.of(path).parent ?: return
        val vf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(parent) ?: return
        com.intellij.openapi.vfs.VfsUtil.markDirtyAndRefresh(true, true, true, vf)
    }

    override fun dispose() {
        sessionService.removeChangeListener(changeListener)
    }

    private companion object {
        const val GIT_COLUMN_MODEL_INDEX = 3

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

private const val RELOAD_COALESCE_MS = 100

private val CARD_ROW_HEIGHT = JBUI.scale(46)

private const val SPIN_RESCAN_MS = 500L

private val GLYPH_STRIP_WIDTH = JBUI.scale(44)
