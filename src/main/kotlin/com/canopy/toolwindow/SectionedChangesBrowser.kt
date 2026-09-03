package com.canopy.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import javax.swing.tree.DefaultTreeModel

class SectionedChangesBrowser(
    project: Project,
    parent: com.intellij.openapi.Disposable,
    private val onRefreshRequested: () -> Unit,
    private val onCommitRequested: () -> Unit,
    private val onPushRequested: () -> Unit,
    private val onOverlapsRequested: () -> Unit,
    private val onNoteRequested: () -> Unit,
    private val onProblemsRequested: () -> Unit,
    private val onCommitSelectionRequested: () -> Unit,
    private val onPushSelectionRequested: () -> Unit
) : ChangesBrowserBase(project, false, false) {

    private var sections: Map<SessionChangeSection, List<Change>> = emptyMap()
    private var unversioned: List<FilePath> = emptyList()
    private var pushedCommits: List<CommitWithChanges> = emptyList()
    private var fileQuery = ""

    init {
        init()
        viewer.emptyText.text = "No changes detected"
        // Module grouping hangs a file on whichever IDEA module claims it, and every file here comes
        // from a checkout this project has no modules for: they landed under another project's
        // module names with ../../.. paths measured from the wrong root, and a submodule appeared
        // nested inside its own superproject. A cross-repository review groups by repository.
        viewer.groupingSupport.setGroupingKeysOrSkip(
            setOf(
                com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport.REPOSITORY_GROUPING,
                com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport.DIRECTORY_GROUPING
            )
        )

        val preview = ReviewDiffPreview(viewer, this)
        com.intellij.openapi.util.Disposer.register(parent, preview)
        setShowDiffActionPreview(preview)
    }

    override fun createToolbarActions(): List<com.intellij.openapi.actionSystem.AnAction> {
        val refresh = object : com.intellij.openapi.actionSystem.AnAction(
            "Refresh",
            "Re-read the working trees",
            com.intellij.icons.AllIcons.Actions.Refresh
        ), com.intellij.openapi.project.DumbAware {
            override fun actionPerformed(event: com.intellij.openapi.actionSystem.AnActionEvent) = onRefreshRequested()

            override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
        }

        val commit = object : com.intellij.openapi.actionSystem.AnAction(
            "Commit All",
            "Commit every repository this session changed",
            com.intellij.icons.AllIcons.Actions.Commit
        ), com.intellij.openapi.project.DumbAware {
            override fun actionPerformed(event: com.intellij.openapi.actionSystem.AnActionEvent) = onCommitRequested()

            override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
        }

        val push = object : com.intellij.openapi.actionSystem.AnAction(
            "Push All",
            "Push every repository this session committed to, submodules first",
            com.intellij.icons.AllIcons.Vcs.Push
        ), com.intellij.openapi.project.DumbAware {
            override fun actionPerformed(event: com.intellij.openapi.actionSystem.AnActionEvent) = onPushRequested()

            override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
        }

        val overlaps = object : com.intellij.openapi.actionSystem.AnAction(
            "Show Overlaps",
            "Files another running session has also written to",
            com.intellij.icons.AllIcons.General.Warning
        ), com.intellij.openapi.project.DumbAware {
            override fun actionPerformed(event: com.intellij.openapi.actionSystem.AnActionEvent) = onOverlapsRequested()

            override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
        }

        val note = object : com.intellij.openapi.actionSystem.AnAction(
            "Send a Note",
            "Ask the session about the selected files, without leaving the diff",
            com.intellij.icons.AllIcons.Actions.AddMulticaret
        ), com.intellij.openapi.project.DumbAware {
            override fun actionPerformed(event: com.intellij.openapi.actionSystem.AnActionEvent) = onNoteRequested()

            override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
        }

        val problems = object : com.intellij.openapi.actionSystem.AnAction(
            "Send Problems",
            "Send the IDE's errors and warnings in these files back to the session",
            com.intellij.icons.AllIcons.General.InspectionsError
        ), com.intellij.openapi.project.DumbAware {
            override fun actionPerformed(event: com.intellij.openapi.actionSystem.AnActionEvent) = onProblemsRequested()

            override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
        }

        return listOf(refresh, commit, push, note, problems, overlaps) + super.createToolbarActions()
    }

    fun selectedPaths(): List<String> {
        val data = com.intellij.openapi.vcs.changes.ui.VcsTreeModelData.selected(viewer)
        val changed = data.iterateUserObjects(Change::class.java)
            .mapNotNull { it.afterRevision?.file?.path ?: it.beforeRevision?.file?.path }
        val untracked = data.iterateUserObjects(FilePath::class.java).map { it.path }

        return (changed.toList() + untracked.toList()).distinct()
    }

    fun allPaths(): List<String> {
        val data = com.intellij.openapi.vcs.changes.ui.VcsTreeModelData.all(viewer)
        val changed = data.iterateUserObjects(Change::class.java)
            .mapNotNull { it.afterRevision?.file?.path ?: it.beforeRevision?.file?.path }
        val untracked = data.iterateUserObjects(FilePath::class.java).map { it.path }

        return (changed.toList() + untracked.toList()).distinct()
    }

    override fun createPopupMenuActions(): List<com.intellij.openapi.actionSystem.AnAction> {
        val ours = listOfNotNull(
            diffAction,
            openSourceAction(),
            com.intellij.openapi.actionSystem.Separator.getInstance(),
            expandSubtreeAction(),
            collapseSubtreeAction(),
            com.intellij.openapi.actionSystem.Separator.getInstance(),
            commitSelectionAction(),
            pushSelectionAction(),
            rollbackAction(),
            com.intellij.openapi.actionSystem.Separator.getInstance(),
            revealAction(),
            copyPathAction()
        )

        // Show Diff is the platform's own action and it is in both lists; adding it twice throws.
        return ours + super.createPopupMenuActions().filterNot { it in ours }
    }

    private fun expandSubtreeAction() = subtreeAction("Expand Subtree", com.intellij.icons.AllIcons.Actions.Expandall) {
        expandSubtree(viewer, it)
    }

    private fun collapseSubtreeAction() = subtreeAction("Collapse Subtree", com.intellij.icons.AllIcons.Actions.Collapseall) {
        collapseSubtree(viewer, it)
    }

    private fun subtreeAction(
        title: String,
        icon: javax.swing.Icon,
        act: (javax.swing.tree.TreePath) -> Unit
    ) = object : com.intellij.openapi.actionSystem.AnAction(title, "$title of the selected row", icon),
        com.intellij.openapi.project.DumbAware {
        override fun actionPerformed(event: com.intellij.openapi.actionSystem.AnActionEvent) {
            selectedBranch()?.let(act)
        }

        override fun update(event: com.intellij.openapi.actionSystem.AnActionEvent) {
            event.presentation.isEnabled = selectedBranch() != null
        }

        override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.EDT
    }

    private fun selectedBranch(): javax.swing.tree.TreePath? = viewer.selectionPath
        ?.takeIf { (it.lastPathComponent as? javax.swing.tree.TreeNode)?.isLeaf == false }

    private fun commitSelectionAction() = object : com.intellij.openapi.actionSystem.AnAction(
        "Commit Selected Files\u2026",
        "Commit only the files under the selection",
        com.intellij.icons.AllIcons.Actions.Commit
    ), com.intellij.openapi.project.DumbAware {
        override fun actionPerformed(event: com.intellij.openapi.actionSystem.AnActionEvent) =
            onCommitSelectionRequested()

        override fun update(event: com.intellij.openapi.actionSystem.AnActionEvent) {
            event.presentation.isEnabled = selectedPaths().isNotEmpty()
        }

        override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.EDT
    }

    private fun rollbackAction(): com.intellij.openapi.actionSystem.AnAction? =
        com.intellij.openapi.actionSystem.ActionManager.getInstance().getAction("ChangesView.Revert")

    private fun pushSelectionAction() = object : com.intellij.openapi.actionSystem.AnAction(
        "Push Their Repositories",
        "Push the repositories the selected files belong to",
        com.intellij.icons.AllIcons.Vcs.Push
    ), com.intellij.openapi.project.DumbAware {
        override fun actionPerformed(event: com.intellij.openapi.actionSystem.AnActionEvent) =
            onPushSelectionRequested()

        override fun update(event: com.intellij.openapi.actionSystem.AnActionEvent) {
            event.presentation.isEnabled = selectedPaths().isNotEmpty()
        }

        override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.EDT
    }

    private fun openSourceAction() = object : com.intellij.openapi.actionSystem.AnAction(
        "Edit Source",
        "Open the file in the editor",
        com.intellij.icons.AllIcons.Actions.EditSource
    ), com.intellij.openapi.project.DumbAware {
        override fun actionPerformed(event: com.intellij.openapi.actionSystem.AnActionEvent) {
            selectedPaths().firstNotNullOfOrNull(::virtualFileAt)?.let {
                com.intellij.openapi.fileEditor.FileEditorManager.getInstance(myProject).openFile(it, true)
            }
        }

        // update() runs on every menu open and every toolbar refresh, and a whole section can be
        // selected: asking the file system about four hundred files each time is not a question
        // for the UI thread. The action opens the first one it can, so probing that far is enough.
        override fun update(event: com.intellij.openapi.actionSystem.AnActionEvent) {
            event.presentation.isEnabled =
                selectedPaths().asSequence().take(OPENABLE_PROBE).any { virtualFileAt(it) != null }
        }

        override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.EDT
    }.also { it.registerCustomShortcutSet(com.intellij.openapi.actionSystem.CommonShortcuts.getEditSource(), this) }

    private fun revealAction() = object : com.intellij.openapi.actionSystem.AnAction(
        com.intellij.ide.actions.RevealFileAction.getActionName(),
        "Show the file in the file manager",
        com.intellij.icons.AllIcons.Actions.MenuOpen
    ), com.intellij.openapi.project.DumbAware {
        override fun actionPerformed(event: com.intellij.openapi.actionSystem.AnActionEvent) {
            selectedPaths().firstOrNull()?.let { com.intellij.ide.actions.RevealFileAction.openFile(java.io.File(it)) }
        }

        override fun update(event: com.intellij.openapi.actionSystem.AnActionEvent) {
            event.presentation.isEnabled = selectedPaths().isNotEmpty()
        }

        override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.EDT
    }

    private fun copyPathAction() = object : com.intellij.openapi.actionSystem.AnAction(
        "Copy Path",
        "Copy the paths of the selected files",
        com.intellij.icons.AllIcons.Actions.Copy
    ), com.intellij.openapi.project.DumbAware {
        override fun actionPerformed(event: com.intellij.openapi.actionSystem.AnActionEvent) {
            val paths = selectedPaths()
            if (paths.isEmpty()) return

            com.intellij.openapi.ide.CopyPasteManager.getInstance()
                .setContents(java.awt.datatransfer.StringSelection(paths.joinToString("\n")))
        }

        override fun update(event: com.intellij.openapi.actionSystem.AnActionEvent) {
            event.presentation.isEnabled = selectedPaths().isNotEmpty()
        }

        override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.EDT
    }

    private fun virtualFileAt(path: String): com.intellij.openapi.vfs.VirtualFile? =
        com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByNioFile(java.nio.file.Path.of(path))

    fun setFileQuery(query: String) {
        if (query == fileQuery) return

        fileQuery = query
        viewer.rebuildTree(com.intellij.openapi.vcs.changes.ui.ChangesTree.ALWAYS_KEEP)
    }

    private fun matching(changes: List<Change>): List<Change> =
        if (fileQuery.isBlank()) changes
        else changes.filter { matchesFileQuery(com.intellij.openapi.vcs.changes.ChangesUtil.getFilePath(it).path, fileQuery) }

    fun setSections(changeSet: SessionChangeSet) {
        sections = changeSet.changes
        unversioned = changeSet.unversioned
        pushedCommits = changeSet.pushedCommits
        viewer.rebuildTree(com.intellij.openapi.vcs.changes.ui.ChangesTree.ALWAYS_KEEP)
        collapsePushedOnce()
    }

    private var pushedCollapsed = false

    private fun collapsePushedOnce() {
        if (pushedCollapsed || sections[SessionChangeSection.Pushed].isNullOrEmpty()) return
        pushedCollapsed = true

        val root = viewer.model.root as? javax.swing.tree.TreeNode ?: return
        for (index in 0 until root.childCount) {
            val node = root.getChildAt(index) as? ChangesBrowserNode<*> ?: continue
            if (!node.userObject.toString().startsWith(SessionChangeSection.Pushed.title)) continue

            viewer.collapsePath(javax.swing.tree.TreePath(node.path))
        }
    }

    override fun buildTreeModel(): DefaultTreeModel {
        val builder = TreeModelBuilder(myProject, grouping)

        for (section in SessionChangeSection.entries) {
            val changes = matching(sections[section].orEmpty())
            if (changes.isEmpty()) continue

            val tag = tagNode(builder, section)
            if (section == SessionChangeSection.Pushed && pushedCommits.isNotEmpty()) {
                insertByCommit(builder, tag)
                continue
            }

            builder.insertChanges(changes, tag)
        }

        val shownUnversioned = unversioned.filter { matchesFileQuery(it.path, fileQuery) }
        if (shownUnversioned.isNotEmpty()) {
            val tag = tagNode(builder, SessionChangeSection.Unversioned)
            shownUnversioned.forEach { builder.insertChangeNode(it, tag, ChangesBrowserNode.createFilePath(it)) }
        }

        return builder.build()
    }

    private fun insertByCommit(builder: TreeModelBuilder, parent: ChangesBrowserNode<*>) {
        val listed = pushedCommits.filter { it.changes.isNotEmpty() }
        val budgeted = withinFileBudget(listed, PUSHED_FILE_BUDGET) { it.changes.size }

        budgeted.kept.forEach { entry ->
            val node = CommitBrowserNode(entry)
            builder.insertSubtreeRoot(node, parent)
            builder.insertChanges(entry.changes, node)
        }

        if (budgeted.omitted == 0) return

        builder.insertSubtreeRoot(
            OlderCommitsNode("${budgeted.omitted} older commits, ${budgeted.omittedFiles} files"),
            parent
        )
    }

    private fun tagNode(builder: TreeModelBuilder, section: SessionChangeSection): ChangesBrowserNode<*> {
        val node = com.intellij.openapi.vcs.changes.ui.TagChangesBrowserNode(
            object : ChangesBrowserNode.Tag {
                override fun toString(): String = section.title
            },
            if (section.isYours) com.intellij.ui.SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
            else com.intellij.ui.SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES,
            true
        )
        builder.insertSubtreeRoot(node)

        return node
    }
}

private const val PUSHED_FILE_BUDGET = 400
private const val OPENABLE_PROBE = 20
