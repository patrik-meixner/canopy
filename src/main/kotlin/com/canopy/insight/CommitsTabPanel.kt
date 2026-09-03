package com.canopy.insight

import com.canopy.toolwindow.CommitEntry
import com.canopy.toolwindow.SessionCommits
import com.canopy.util.CanopyExecutor
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.SimpleChangesBrowser
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.DefaultListModel
import javax.swing.JList
import javax.swing.ListSelectionModel

class CommitsTabPanel(project: Project, parent: Disposable) : InsightTabPanel(project, parent) {

    private val model = DefaultListModel<CommitEntry>()
    private val commits = ViewportWidthList(model)
    private val files = SimpleChangesBrowser(project, false, true)
    private var loadedFor: String? = null
    private var loadedRoots: List<Pair<String, String>> = emptyList()
    private var collected: List<CommitEntry> = emptyList()
    private var shownChanges: List<com.intellij.openapi.vcs.changes.Change> = emptyList()
    private val fileSearch = com.intellij.ui.SearchTextField(false).apply {
        textEditor.emptyText.text = "Filter files in the selected commits"
        border = JBUI.Borders.empty(2, InsightUi.GAP)
        isOpaque = false
        addDocumentListener(object : com.intellij.ui.DocumentAdapter() {
            override fun textChanged(event: javax.swing.event.DocumentEvent) = applyFileQuery()
        })
    }
    private val known = LinkedHashMap<String, List<CommitEntry>>()
    private var limit = COMMIT_PAGE
    private val split = JBSplitter(true, "canopy.commits.split", 0.45f)
    private val body = ContentOrEmpty(
        split,
        EmptyState(
            AllIcons.Vcs.CommitNode,
            "No commits yet",
            "What this session committed, in every repository it wrote to, with the files behind each one."
        )
    )
    private val more = MoreRow {
        limit += COMMIT_PAGE
        showPage()
    }

    init {
        background = InsightUi.panelBackground()
        commits.cellRenderer = CommitRenderer()
        commits.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        commits.emptyText.text = "This session has committed nothing yet"
        commits.border = JBUI.Borders.empty(InsightUi.GAP, InsightUi.GAP)
        commits.addListSelectionListener { if (!it.valueIsAdjusting) showFilesOf(commits.selectedValuesList) }

        files.hideViewerBorder()
        val preview = com.canopy.toolwindow.ReviewDiffPreview(files.viewer, files)
        com.intellij.openapi.util.Disposer.register(parent, preview)
        files.setShowDiffActionPreview(preview)

        com.canopy.services.WorkspaceChangeNotifier.getInstance(project).addListener(parent) {
            if (isShowing && loadedRoots.isNotEmpty()) reload(loadedRoots)
        }

        split.firstComponent = JPanelWithMore(ScrollPaneFactory.createScrollPane(commits, true), more)
        split.secondComponent = javax.swing.JPanel(java.awt.BorderLayout()).apply {
            isOpaque = false
            add(fileSearch, java.awt.BorderLayout.NORTH)
            add(files, java.awt.BorderLayout.CENTER)
        }

        add(toolbar().component, BorderLayout.NORTH)
        add(body, BorderLayout.CENTER)
    }

    private fun toolbar(): com.intellij.openapi.actionSystem.ActionToolbar {
        val refresh = object : com.intellij.openapi.actionSystem.AnAction(
            "Refresh",
            "Re-read the commits of this session",
            com.intellij.icons.AllIcons.Actions.Refresh
        ), com.intellij.openapi.project.DumbAware {
            override fun actionPerformed(event: com.intellij.openapi.actionSystem.AnActionEvent) {
                loadedFor = null
                if (loadedRoots.isNotEmpty()) reload(loadedRoots)
            }

            override fun update(event: com.intellij.openapi.actionSystem.AnActionEvent) {
                event.presentation.isEnabled = loadedRoots.isNotEmpty()
            }

            override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.EDT
        }

        return com.intellij.openapi.actionSystem.ActionManager.getInstance()
            .createActionToolbar(
                "CanopyCommitsToolbar",
                com.intellij.openapi.actionSystem.DefaultActionGroup(refresh),
                true
            )
            .also { it.targetComponent = this }
    }

    override fun render(insight: SessionInsight) {
        val session = selectedSession()?.sessionId ?: return
        val roots = insight.files.map { it.repository to it.path }.distinctBy { it.first }
        val signature = "$session:${insight.revision}"
        if (signature == loadedFor) return
        loadedFor = signature
        loadedRoots = roots

        known[session]?.let { show(it) }
        reload(roots)
    }

    private fun show(commits: List<CommitEntry>) {
        collected = commits
        showPage()
    }

    private fun showPage() {
        val kept = commits.selectedValuesList.mapTo(HashSet()) { it.sha }
        val page = pageful(collected, limit)

        more.show(page.remaining, COMMIT_PAGE)
        body.showEmpty(collected.isEmpty())
        model.clear()
        page.shown.forEach { model.addElement(it) }
        val rows = (0 until model.size()).filter { model[it].sha in kept }
        if (rows.isNotEmpty()) commits.selectedIndices = rows.toIntArray()
    }

    private fun reload(roots: List<Pair<String, String>>) {
        val since = insightStart()

        CanopyExecutor.submit {
            val logs = roots
                .mapNotNull { (repository, path) -> repositoryRootOf(path)?.let { repository to it } }
                .distinctBy { it.second }
                .map { (repository, root) ->
                    java.util.concurrent.Callable { SessionCommits.list(root, repository, since) }
                }
            val collected = com.intellij.util.concurrency.AppExecutorUtil.getAppExecutorService()
                .invokeAll(logs)
                .flatMap { it.get() }
                .sortedByDescending { it.atMillis }

            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater

                show(collected)
                selectedSession()?.sessionId?.let { id ->
                    known[id] = collected
                    if (known.size > REMEMBERED) known.remove(known.keys.first())
                }
            }
        }
    }

    private fun insightStart(): java.time.Instant? {
        val sessionId = selectedSession()?.sessionId ?: return null

        return com.canopy.services.ClaudeSessionService.getInstance(project).cachedStartedAt(sessionId)
    }

    private fun repositoryRootOf(path: String): String? =
        com.canopy.util.GitRootCache.rootOf(com.canopy.util.directoryOf(java.nio.file.Path.of(path)))

    private fun applyFileQuery() {
        val query = fileSearch.text.orEmpty()

        files.setChangesToDisplay(
            shownChanges.filter {
                val path = it.afterRevision?.file?.path ?: it.beforeRevision?.file?.path.orEmpty()
                com.canopy.toolwindow.matchesFileQuery(path, query)
            }
        )
    }

    private fun showFilesOf(selected: List<CommitEntry>) {
        if (selected.isEmpty()) {
            shownChanges = emptyList()
            return files.setChangesToDisplay(emptyList())
        }

        val shas = selected.map { it.sha }

        CanopyExecutor.submit {
            val perCommit = selected.flatMap { com.canopy.toolwindow.SessionCommits.changesIn(it.root, it.sha) }
            val changes = com.canopy.toolwindow.unionByPath(perCommit) {
                it.afterRevision?.file?.path ?: it.beforeRevision?.file?.path
            }

            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed || commits.selectedValuesList.map { it.sha } != shas) return@invokeLater

                shownChanges = changes
                applyFileQuery()
            }
        }
    }

    private class CommitRenderer : ColoredListCellRenderer<CommitEntry>() {
        override fun customizeCellRenderer(
            list: JList<out CommitEntry>,
            value: CommitEntry,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean
        ) {
            icon = AllIcons.Vcs.CommitNode
            append(value.subject, SimpleTextAttributes.REGULAR_ATTRIBUTES)
            append("   ${value.repository}", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
            append("  ${com.canopy.toolwindow.relativeAge(value.atMillis, System.currentTimeMillis())}",
                SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
            toolTipText = "${value.sha.take(8)}  ${value.author}"
        }
    }
}

private const val COMMIT_PAGE = 50
private const val REMEMBERED = 8
