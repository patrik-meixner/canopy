package com.canopy.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import javax.swing.tree.DefaultTreeModel

/**
 * The Commit window's tree, sectioned by how far a change has travelled rather than by which
 * changelist it belongs to.
 */
class SectionedChangesBrowser(
    project: Project,
    private val onRefreshRequested: () -> Unit,
    private val onCommitRequested: () -> Unit,
    private val onPushRequested: () -> Unit,
    private val onOverlapsRequested: () -> Unit,
    private val onNoteRequested: () -> Unit,
    private val onProblemsRequested: () -> Unit,
    private val onRestoreRequested: () -> Unit,
    private val onCommitSelectionRequested: () -> Unit,
    private val onPushSelectionRequested: () -> Unit
) : ChangesBrowserBase(project, false, false) {

    private var sections: Map<SessionChangeSection, List<Change>> = emptyMap()
    private var unversioned: List<FilePath> = emptyList()
    private var pushedCommits: List<CommitWithChanges> = emptyList()

    init {
        init()
        viewer.emptyText.text = "No changes detected"
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

        val restore = object : com.intellij.openapi.actionSystem.AnAction(
            "Restore a Checkpoint",
            "Put the working tree back as it stood at the end of an earlier turn",
            com.intellij.icons.AllIcons.Actions.Rollback
        ), com.intellij.openapi.project.DumbAware {
            override fun actionPerformed(event: com.intellij.openapi.actionSystem.AnActionEvent) = onRestoreRequested()

            override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
        }

        return listOf(refresh, commit, push, note, problems, restore, overlaps) + super.createToolbarActions()
    }

    /** What a note is about: the selected rows, and their untracked files as well as their changes. */
    fun selectedPaths(): List<String> {
        val data = com.intellij.openapi.vcs.changes.ui.VcsTreeModelData.selected(viewer)
        val changed = data.iterateUserObjects(Change::class.java)
            .mapNotNull { it.afterRevision?.file?.path ?: it.beforeRevision?.file?.path }
        val untracked = data.iterateUserObjects(FilePath::class.java).map { it.path }

        return (changed.toList() + untracked.toList()).distinct()
    }

    /** Every file in the tree, for the checks that are about the change as a whole. */
    fun allPaths(): List<String> {
        val data = com.intellij.openapi.vcs.changes.ui.VcsTreeModelData.all(viewer)
        val changed = data.iterateUserObjects(Change::class.java)
            .mapNotNull { it.afterRevision?.file?.path ?: it.beforeRevision?.file?.path }
        val untracked = data.iterateUserObjects(FilePath::class.java).map { it.path }

        return (changed.toList() + untracked.toList()).distinct()
    }

    /**
     * The same menu the Commit and Log windows have: a row here is a file, and a file opens.
     *
     * Show Diff stays first because reviewing is why the tree exists, but reading a change usually
     * ends in wanting the file itself.
     */
    override fun createPopupMenuActions(): List<com.intellij.openapi.actionSystem.AnAction> =
        listOf(
            diffAction,
            openSourceAction(),
            com.intellij.openapi.actionSystem.Separator.getInstance(),
            commitSelectionAction(),
            pushSelectionAction(),
            com.intellij.openapi.actionSystem.Separator.getInstance(),
            revealAction(),
            copyPathAction()
        ) + super.createPopupMenuActions()

    /**
     * A directory row stands for the files under it, so committing one commits those and nothing
     * else. Pushing has no directory-sized form: a push moves a repository, so the selection only
     * says which repositories are meant.
     */
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

        override fun update(event: com.intellij.openapi.actionSystem.AnActionEvent) {
            event.presentation.isEnabled = selectedPaths().any { virtualFileAt(it) != null }
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

    /** A deleted file has a path in the tree and nothing on disk, so Edit Source has to stay off. */
    private fun virtualFileAt(path: String): com.intellij.openapi.vfs.VirtualFile? =
        com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByNioFile(java.nio.file.Path.of(path))

    fun setSections(changeSet: SessionChangeSet) {
        sections = changeSet.changes
        unversioned = changeSet.unversioned
        pushedCommits = changeSet.pushedCommits
        // Keeping the state stops a tab switch from throwing away what was expanded and collapsed.
        viewer.rebuildTree(com.intellij.openapi.vcs.changes.ui.ChangesTree.ALWAYS_KEEP)
        collapsePushedOnce()
    }

    private var pushedCollapsed = false

    /** Pushed work is history and outnumbers the rest; it opens closed, and stays how you leave it. */
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
            val changes = sections[section].orEmpty()
            if (changes.isEmpty()) continue

            val tag = tagNode(builder, section)
            // A hundred pushed files are a handful of commits, and the commit is what they mean.
            if (section == SessionChangeSection.Pushed && pushedCommits.isNotEmpty()) {
                insertByCommit(builder, tag)
                continue
            }

            builder.insertChanges(changes, tag)
        }

        if (unversioned.isNotEmpty()) builder.setUnversioned(unversioned)

        return builder.build()
    }

    private fun insertByCommit(builder: TreeModelBuilder, parent: ChangesBrowserNode<*>) {
        for (entry in pushedCommits) {
            if (entry.changes.isEmpty()) continue
            val node = CommitBrowserNode(entry)
            builder.insertSubtreeRoot(node, parent)
            builder.insertChanges(entry.changes, node)
        }
    }

    private fun tagNode(builder: TreeModelBuilder, section: SessionChangeSection): ChangesBrowserNode<*> {
        // A header that says what the state means, and reads quieter once the work has left.
        val node = com.intellij.openapi.vcs.changes.ui.TagChangesBrowserNode(
            object : ChangesBrowserNode.Tag {
                override fun toString(): String = "${section.title}  \u00b7  ${section.hint}"
            },
            if (section.isYours) com.intellij.ui.SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
            else com.intellij.ui.SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES,
            true
        )
        builder.insertSubtreeRoot(node)

        return node
    }
}
