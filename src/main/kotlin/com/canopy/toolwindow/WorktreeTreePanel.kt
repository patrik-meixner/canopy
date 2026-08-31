package com.canopy.toolwindow

import com.canopy.services.RepoScopeService
import com.canopy.util.CanopyExecutor
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.RevealFileAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.PopupHandler
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.event.HierarchyEvent
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

/**
 * Worktrees grouped by the repo that owns them: the superproject and each initialized submodule.
 *
 * Refreshes cost one `git worktree list` per repo, so they run off the EDT and only while the
 * panel is on screen, under the same floor the session sweep uses.
 */
class WorktreeTreePanel(
    private val project: Project,
    parent: Disposable,
    private val onWorktreeSelected: (WorktreeTreeNode.Worktree?) -> Unit,
    private val onWorktreeActivated: (WorktreeTreeNode.Worktree) -> Unit,
    private val loadSessions: () -> List<com.canopy.model.SessionDisplay>,
    private val attentionOf: (com.canopy.model.SessionDisplay) -> com.canopy.model.SessionAttention,
    private val onSessionActivated: (com.canopy.model.SessionDisplay) -> Unit,
    private val onNewWorktree: (com.canopy.model.RepoScope, String) -> Unit
) : JPanel(BorderLayout()), Disposable {

    private val root = DefaultMutableTreeNode()
    private val treeModel = DefaultTreeModel(root)
    private val tree = Tree(treeModel)
    private val refreshInFlight = AtomicBoolean(false)

    /** Its own cache: the tree is now the only place worktree git state is shown. */
    private val worktreeStatus = WorktreeStatusCache(project, this) { tree.repaint() }

    /** Branches come from the same sweep: one pass over the repositories answers both sections. */
    private var branches: Map<String, List<BranchEntry>> = emptyMap()

    @Volatile private var onScreen = false
    @Volatile private var lastRefreshAt = 0L

    init {
        Disposer.register(parent, this)

        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        tree.cellRenderer = WorktreeTreeCellRenderer()
        tree.border = JBUI.Borders.empty(2)

        tree.addTreeSelectionListener { onWorktreeSelected(selectedWorktree()) }

        object : DoubleClickListener() {
            override fun onDoubleClick(event: java.awt.event.MouseEvent): Boolean {
                selectedSession()?.let { onSessionActivated(it.session); return true }
                val worktree = selectedWorktree() ?: return false

                onWorktreeActivated(worktree)
                return true
            }
        }.installOn(tree)

        installContextMenu()
        worktreeStatus.trackVisibility(this)

        add(JBScrollPane(tree), BorderLayout.CENTER)

        addHierarchyListener { event ->
            if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() == 0L) return@addHierarchyListener

            onScreen = isShowing
            if (onScreen) refresh(force = true)
        }
    }

    private fun selectedWorktree(): WorktreeTreeNode.Worktree? = selectedNode() as? WorktreeTreeNode.Worktree

    private fun selectedSession(): WorktreeTreeNode.Session? = selectedNode() as? WorktreeTreeNode.Session

    private fun selectedRepo(): WorktreeTreeNode.Repo? = selectedNode() as? WorktreeTreeNode.Repo

    /** Every row belongs to a repository, so a repository action never needs the repository row. */
    private fun scopeOfSelection(): com.canopy.model.RepoScope? =
        selectedRepo()?.scope ?: selectedWorktree()?.scope ?: selectedBranch()?.scope

    private fun selectedBranch(): WorktreeTreeNode.Branch? = selectedNode() as? WorktreeTreeNode.Branch

    private fun selectedNode(): WorktreeTreeNode? {
        val node = tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode ?: return null

        return node.userObject as? WorktreeTreeNode
    }

    private fun installContextMenu() {
        val group = DefaultActionGroup().apply {
            add(object : AnAction("New Worktree Here", "Create a worktree in this repository", AllIcons.General.Add) {
                override fun actionPerformed(e: AnActionEvent) {
                    val scope = scopeOfSelection() ?: return
                    val name = Messages.showInputDialog(
                        project,
                        "Worktree name:",
                        "New Worktree in ${scope.label}",
                        null
                    )?.trim().orEmpty()
                    if (name.isEmpty()) return

                    onNewWorktree(scope, name)
                }

                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = scopeOfSelection() != null
                }

                override fun getActionUpdateThread() = ActionUpdateThread.EDT
            })
            add(object : AnAction("New Session Here", "Start a Claude session in this worktree", AllIcons.General.Add) {
                override fun actionPerformed(e: AnActionEvent) {
                    selectedWorktree()?.let(onWorktreeActivated)
                }

                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = selectedWorktree() != null
                }

                override fun getActionUpdateThread() = ActionUpdateThread.EDT
            })
            add(object : AnAction("Show Changes", "Diff this worktree against its base branch", AllIcons.Actions.Diff) {
                override fun actionPerformed(e: AnActionEvent) {
                    selectedWorktree()?.let(::showChanges)
                }

                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = selectedWorktree() != null
                }

                override fun getActionUpdateThread() = ActionUpdateThread.EDT
            })
            addSeparator()
            add(object : AnAction("Set Up Worktree", "Copy the untracked files it needs, then run the install command", AllIcons.Actions.Install) {
                override fun actionPerformed(e: AnActionEvent) {
                    val worktree = selectedWorktree() ?: return

                    provision(worktree.scope, worktree.entry.path)
                }

                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = selectedWorktree() != null
                }

                override fun getActionUpdateThread() = ActionUpdateThread.EDT
            })
            add(object : AnAction("Configure Worktree Setup\u2026", "Choose the ignored files and install command this repository's worktrees need", AllIcons.General.Settings) {
                override fun actionPerformed(e: AnActionEvent) {
                    val scope = scopeOfSelection() ?: return

                    WorktreeSetupDialog(project, scope.label, scope.root).show()
                }

                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = scopeOfSelection() != null
                }

                override fun getActionUpdateThread() = ActionUpdateThread.EDT
            })
            addSeparator()
            add(object : AnAction("Run in Several Worktrees\u2026", "Start one prompt in several worktrees at once", AllIcons.Actions.RunAll) {
                override fun actionPerformed(e: AnActionEvent) {
                    val scope = scopeOfSelection() ?: return

                    fanOut(scope)
                }

                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = scopeOfSelection() != null
                }

                override fun getActionUpdateThread() = ActionUpdateThread.EDT
            })
            add(object : AnAction("New Worktree from Branch", "Check this branch out in its own worktree", AllIcons.General.Add) {
                override fun actionPerformed(e: AnActionEvent) {
                    val branch = selectedBranch() ?: return

                    onNewWorktree(branch.scope, branch.entry.name)
                }

                override fun update(e: AnActionEvent) {
                    val branch = selectedBranch()
                    e.presentation.isEnabled = branch != null && branch.entry.worktreePath == null
                }

                override fun getActionUpdateThread() = ActionUpdateThread.EDT
            })
            addSeparator()
            add(object : AnAction("Disk Usage", "Measure what this repository's worktrees cost on disk", AllIcons.Actions.Profile) {
                override fun actionPerformed(e: AnActionEvent) {
                    val scope = scopeOfSelection() ?: return

                    reportDiskUsage(scope, worktreesOf(scope))
                }

                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = scopeOfSelection() != null
                }

                override fun getActionUpdateThread() = ActionUpdateThread.EDT
            })
            add(object : AnAction("Prune Registrations", "Drop git's entries for worktree directories that are gone", AllIcons.Actions.GC) {
                override fun actionPerformed(e: AnActionEvent) {
                    val scope = scopeOfSelection() ?: return

                    pruneRegistrations(scope)
                }

                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = scopeOfSelection() != null
                }

                override fun getActionUpdateThread() = ActionUpdateThread.EDT
            })
            addSeparator()
            add(object : AnAction("Delete Merged Worktrees\u2026", "Find and remove worktrees with nothing left in them", AllIcons.Actions.GC) {
                override fun actionPerformed(e: AnActionEvent) {
                    val scope = scopeOfSelection() ?: return

                    cleanUp(scope)
                }

                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = scopeOfSelection() != null
                }

                override fun getActionUpdateThread() = ActionUpdateThread.EDT
            })
            add(object : AnAction("Delete Worktree", "Remove this worktree and its directory", AllIcons.Actions.GC) {
                override fun actionPerformed(e: AnActionEvent) {
                    val worktree = selectedWorktree() ?: return

                    deleteWorktree(worktree)
                }

                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = selectedWorktree() != null
                }

                override fun getActionUpdateThread() = ActionUpdateThread.EDT
            })
            add(object : AnAction("Delete Branch", "Delete this branch, if it has been merged", AllIcons.Actions.GC) {
                override fun actionPerformed(e: AnActionEvent) {
                    val branch = selectedBranch() ?: return

                    deleteBranch(branch)
                }

                override fun update(e: AnActionEvent) {
                    val branch = selectedBranch()
                    // git refuses to delete the branch a worktree has checked out, and so does this.
                    e.presentation.isEnabled = branch != null &&
                        branch.entry.worktreePath == null &&
                        !branch.entry.isCurrent
                }

                override fun getActionUpdateThread() = ActionUpdateThread.EDT
            })
            addSeparator()
            add(object : AnAction("Reveal in Finder", "Open the worktree directory", AllIcons.Actions.MenuOpen) {
                override fun actionPerformed(e: AnActionEvent) {
                    val path = selectedWorktree()?.entry?.path ?: return

                    RevealFileAction.openDirectory(java.io.File(path))
                }

                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = selectedWorktree() != null
                }

                override fun getActionUpdateThread() = ActionUpdateThread.EDT
            })
        }
        PopupHandler.installPopupMenu(tree, group, "CanopyWorktreeTree")
    }

    /** Asked of the repository, not of the selected row, so it answers the same from any of them. */
    private fun worktreesOf(scope: com.canopy.model.RepoScope): List<Pair<String, String>> =
        WorktreeInspector.list(scope.root).map { (Path.of(it.path).fileName?.toString() ?: it.path) to it.path }

    /** Each worktree gets its own session, and each session gets the same prompt once it is up. */
    private fun fanOut(scope: com.canopy.model.RepoScope) {
        val existing = WorktreeInspector.list(scope.root)
            .mapNotNull { Path.of(it.path).fileName?.toString() }
            .toSet()
        val dialog = FanOutDialog(project, scope, existing)
        if (!dialog.showAndGet()) return
        val plan = dialog.plan() ?: return

        plan.names.forEach { onNewWorktree(scope, it) }
        com.canopy.services.FanOutPrompts.getInstance(project).expect(plan.names, plan.prompt)
    }

    /** The sweep is conservative on purpose: it proposes, and every removal is still confirmed. */
    private fun cleanUp(scope: com.canopy.model.RepoScope) {
        val inUse = loadSessions().mapNotNull { it.worktreeName }.toSet()

        CanopyExecutor.submit {
            val entries = WorktreeInspector.list(scope.root)
            val candidates = cleanupCandidates(entries, { name -> worktreeStatus.get(name) }, inUse)

            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                if (candidates.isEmpty()) {
                    return@invokeLater notify("Nothing to clean up in ${scope.label}")
                }

                val dialog = CleanupDialog(project, candidates)
                if (!dialog.showAndGet()) return@invokeLater

                removeAll(scope, dialog.selected())
            }
        }
    }

    private fun removeAll(scope: com.canopy.model.RepoScope, chosen: List<CleanupCandidate>) {
        if (chosen.isEmpty()) return

        CanopyExecutor.submit {
            val failures = chosen.mapNotNull { candidate ->
                val (removed, output) = WorktreeInspector.remove(scope.root, candidate.path, force = false)
                candidate.branch?.takeIf { removed }?.let { WorktreeInspector.deleteBranchIfMerged(scope.root, it) }
                if (removed) null else "${candidate.name}: ${output.take(120)}"
            }

            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater

                val removed = chosen.size - failures.size
                val tail = if (failures.isEmpty()) "" else "<br>" + failures.joinToString("<br>")
                notify("Removed $removed of ${chosen.size} worktree(s)$tail")
                refresh(force = true)
            }
        }
    }

    /** Removal is not undoable, so it names what will go and what would be lost with it. */
    private fun deleteWorktree(worktree: WorktreeTreeNode.Worktree) {
        val name = Path.of(worktree.entry.path).fileName?.toString() ?: worktree.entry.path
        val ahead = worktree.status?.ahead ?: 0
        val dirty = worktree.status?.dirtyCount ?: 0
        val losses = listOfNotNull(
            dirty.takeIf { it > 0 }?.let { "$it uncommitted file(s)" },
            ahead.takeIf { it > 0 }?.let { "$it commit(s) not in the base branch" }
        )
        val warning = if (losses.isEmpty()) "" else "\n\nThis would lose ${losses.joinToString(" and ")}."

        val confirmed = Messages.showYesNoDialog(
            project,
            "Remove the worktree $name and its directory?$warning",
            "Delete Worktree",
            null
        )
        if (confirmed != Messages.YES) return

        CanopyExecutor.submit {
            val (removed, output) = WorktreeInspector.remove(worktree.scope.root, worktree.entry.path, force = losses.isNotEmpty())

            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater

                notify(if (removed) "Removed $name" else "Could not remove $name: ${output.take(200)}")
                refresh(force = true)
            }
        }
    }

    private fun deleteBranch(branch: WorktreeTreeNode.Branch) {
        val confirmed = Messages.showYesNoDialog(
            project,
            "Delete the branch ${branch.entry.name}?",
            "Delete Branch",
            null
        )
        if (confirmed != Messages.YES) return

        CanopyExecutor.submit {
            val (deleted, output) = WorktreeInspector.deleteBranchIfMerged(branch.scope.root, branch.entry.name)

            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater

                // git -d refuses an unmerged branch; saying so beats a silent failure.
                notify(if (deleted) "Deleted ${branch.entry.name}" else "Not deleted: ${output.take(200)}")
                refresh(force = true)
            }
        }
    }

    private fun provision(scope: com.canopy.model.RepoScope, targetPath: String) {
        val copied = WorktreeProvisioning.provision(project, scope.label, scope.root, targetPath)

        notify(if (copied.isEmpty()) "Nothing to copy" else "Copied ${copied.joinToString(", ")}")
    }

    /** Walking a worktree with installed dependencies takes seconds, so it never runs on the EDT. */
    private fun reportDiskUsage(scope: com.canopy.model.RepoScope, worktrees: List<Pair<String, String>>) {
        if (worktrees.isEmpty()) {
            return notify("${scope.label} has no worktrees")
        }

        CanopyExecutor.submit {
            val measured = worktrees.map { (name, path) -> name to WorktreeDiskUsage.measure(path) }
            val total = measured.sumOf { it.second ?: 0L }
            val lines = measured
                .sortedByDescending { it.second ?: 0L }
                .joinToString("<br>") { (name, bytes) -> "$name: " + (bytes?.let(::formatSize) ?: "unreadable") }

            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater

                notify("${scope.label} worktrees: ${formatSize(total)}<br>$lines")
            }
        }
    }

    private fun pruneRegistrations(scope: com.canopy.model.RepoScope) {
        CanopyExecutor.submit {
            val (succeeded, output) = WorktreeInspector.prune(scope.root)

            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater

                notify(if (succeeded) output.ifBlank { "Nothing to prune" } else "Prune failed: $output")
                refresh(force = true)
            }
        }
    }

    private fun notify(message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Canopy")
            .createNotification(message, NotificationType.INFORMATION)
            .notify(project)
    }

    /** Runs against the worktree directory, so a submodule's worktree diffs against its own repo. */
    private fun showChanges(worktree: WorktreeTreeNode.Worktree) {
        val directory = worktree.entry.path
        val title = "${worktree.scope.label}: ${Path.of(directory).fileName}"

        CanopyExecutor.submit {
            val result = WorktreeChanges.compute(directory)
            ApplicationManager.getApplication().invokeLater {
                if (result.changes.isEmpty()) {
                    return@invokeLater Messages.showInfoMessage(
                        project,
                        result.error ?: "No changes in this worktree.",
                        "Worktree Changes"
                    )
                }

                WorktreeChanges.openDialog(project, title, directory, result.changes)
            }
        }
    }

    fun refresh(force: Boolean = false) {
        if (!onScreen) return
        if (!force && System.currentTimeMillis() - lastRefreshAt < com.canopy.settings.CanopySettings.getInstance().state.worktreeRefreshSeconds * 1000L) return
        if (!refreshInFlight.compareAndSet(false, true)) return

        val scopeService = RepoScopeService.getInstance(project)
        scopeService.refreshIfStale { refresh(force = true) }

        CanopyExecutor.submit {
            try {
                val collected = scopeService.cachedScopes().map { WorktreeInspector.collect(it) }
                val listed = collected.associate { it.scope.root to BranchInventory.list(it.scope.root, it.worktrees) }
                val sessions = loadSessions()
                lastRefreshAt = System.currentTimeMillis()
                ApplicationManager.getApplication().invokeLater {
                    branches = listed
                    rebuild(collected, sessions)
                }
            } finally {
                refreshInFlight.set(false)
            }
        }
    }

    private fun rebuild(
        collected: List<com.canopy.model.RepoWorktrees>,
        sessions: List<com.canopy.model.SessionDisplay>
    ) {
        if (Disposer.isDisposed(this)) return

        val expandedLabels = expandedRepoLabels()
        val byWorktreeName = sessions.filter { it.worktreeName != null }.groupBy { it.worktreeName }
        val placed = byWorktreeName.values.flatten().toSet()
        root.removeAllChildren()

        val worktreeSection = DefaultMutableTreeNode(
            WorktreeTreeNode.Section("Worktrees", collected.sumOf { it.worktrees.size })
        )
        for (repo in collected) {
            val repoNode = DefaultMutableTreeNode(
                WorktreeTreeNode.Repo(repo.scope, repo.branch, repo.worktrees.size)
            )
            for (entry in repo.worktrees) {
                val name = Path.of(entry.path).fileName?.toString() ?: entry.path
                val worktreeNode = DefaultMutableTreeNode(
                    WorktreeTreeNode.Worktree(repo.scope, entry, worktreeStatus.get(name))
                )
                byWorktreeName[name].orEmpty().forEach { worktreeNode.add(sessionNode(it)) }
                repoNode.add(worktreeNode)
            }
            sessionsInCheckout(sessions - placed, repo.scope.root).forEach { repoNode.add(sessionNode(it)) }
            worktreeSection.add(repoNode)
        }
        root.add(worktreeSection)

        val branchSection = DefaultMutableTreeNode(
            WorktreeTreeNode.Section("Branches", branches.values.sumOf { it.size })
        )
        for (repo in collected) {
            val listed = branches[repo.scope.root].orEmpty()
            if (listed.isEmpty()) continue
            val repoNode = DefaultMutableTreeNode(WorktreeTreeNode.Repo(repo.scope, repo.branch, listed.size))
            listed.forEach { repoNode.add(DefaultMutableTreeNode(WorktreeTreeNode.Branch(repo.scope, it))) }
            branchSection.add(repoNode)
        }
        if (branchSection.childCount > 0) root.add(branchSection)

        treeModel.reload()
        (0 until root.childCount).forEach { tree.expandPath(TreePath((root.getChildAt(it) as DefaultMutableTreeNode).path)) }
        expandRepos(expandedLabels)

        worktreeStatus.setTargets(collected.flatMap { it.worktrees }.map { worktreeNameOf(it.path) })
        worktreeStatus.requestRefresh()
    }

    private fun worktreeNameOf(path: String): String = Path.of(path).fileName?.toString() ?: path

    /** A session with no worktree belongs to the repo it wrote to most. */
    private fun sessionsInCheckout(sessions: List<com.canopy.model.SessionDisplay>, root: String) =
        sessions.filter { session ->
            val primary = session.touchedRoots.maxByOrNull { it.value }?.key
            primary == root || (primary == null && session.projectPath == root)
        }

    private fun sessionNode(session: com.canopy.model.SessionDisplay) =
        DefaultMutableTreeNode(WorktreeTreeNode.Session(session, attentionOf(session)))

    /** Rebuilding replaces every node, so the user's expanded repos have to be restored by label. */
    private fun expandedRepoLabels(): Set<String> {
        if (root.childCount == 0) return emptySet()

        return (0 until root.childCount)
            .map { root.getChildAt(it) as DefaultMutableTreeNode }
            .filter { tree.isExpanded(javax.swing.tree.TreePath(it.path)) }
            .mapNotNull { (it.userObject as? WorktreeTreeNode.Repo)?.scope?.label }
            .toSet()
    }

    private fun expandRepos(labels: Set<String>) {
        for (index in 0 until root.childCount) {
            val node = root.getChildAt(index) as DefaultMutableTreeNode
            val label = (node.userObject as? WorktreeTreeNode.Repo)?.scope?.label ?: continue
            val isFirstBuild = labels.isEmpty()
            if (isFirstBuild || label in labels) tree.expandPath(javax.swing.tree.TreePath(node.path))
        }
    }

    override fun dispose() = Unit

}

private const val BRANCHES = "Branches"

private class WorktreeTreeCellRenderer : ColoredTreeCellRenderer() {

    override fun customizeCellRenderer(
        tree: JTree,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ) {
        when (val node = (value as? DefaultMutableTreeNode)?.userObject) {
            is WorktreeTreeNode.Section -> renderSection(node)
            is WorktreeTreeNode.Repo -> renderRepo(node)
            is WorktreeTreeNode.Worktree -> renderWorktree(node)
            is WorktreeTreeNode.Branch -> renderBranch(node)
            is WorktreeTreeNode.Session -> renderSession(node)
        }
    }

    private fun renderSection(node: WorktreeTreeNode.Section) {
        icon = if (node.title == BRANCHES) AllIcons.Vcs.Branch else AllIcons.Nodes.Folder
        append(node.title, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
        append("  ${node.count}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }

    private fun renderBranch(node: WorktreeTreeNode.Branch) {
        icon = AllIcons.Vcs.Branch
        append(
            node.entry.name,
            if (node.entry.isCurrent) SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
            else SimpleTextAttributes.REGULAR_ATTRIBUTES
        )
        append("   ${branchNote(node.entry)}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }

    private fun renderRepo(node: WorktreeTreeNode.Repo) {
        icon = if (node.scope.isSubmodule) AllIcons.Nodes.Module else AllIcons.Nodes.ModuleGroup
        append(node.scope.label, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
        node.branch?.let { append("  $it", SimpleTextAttributes.GRAYED_ATTRIBUTES) }
        if (node.worktreeCount == 0) append("  no worktrees", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }

    private fun renderWorktree(node: WorktreeTreeNode.Worktree) {
        icon = AllIcons.Vcs.Branch
        append(Path.of(node.entry.path).fileName?.toString() ?: node.entry.path)
        node.entry.branch?.let { append("  $it", SimpleTextAttributes.GRAYED_ATTRIBUTES) }

        val dirtyCount = node.status?.dirtyCount ?: 0
        if (dirtyCount > 0) {
            append("  $dirtyCount uncommitted", SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, DIRTY_COLOR))
        }
    }

    private fun renderSession(node: WorktreeTreeNode.Session) {
        icon = com.canopy.editor.ClaudeSessionIconProvider.TREE_ICON
        val glyph = node.attention.glyph
        if (glyph.isNotEmpty()) append("$glyph ", SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, DIRTY_COLOR))
        append(node.session.displayName)
    }

    private companion object {
        val DIRTY_COLOR = com.canopy.insight.InsightUi.waiting()
    }
}
