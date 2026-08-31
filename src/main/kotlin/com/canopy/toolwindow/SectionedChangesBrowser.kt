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
    private val onRestoreRequested: () -> Unit
) : ChangesBrowserBase(project, false, false) {

    private var sections: Map<SessionChangeSection, List<Change>> = emptyMap()
    private var unversioned: List<FilePath> = emptyList()

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

    fun setSections(changeSet: SessionChangeSet) {
        sections = changeSet.changes
        unversioned = changeSet.unversioned
        viewer.rebuildTree()
    }

    override fun buildTreeModel(): DefaultTreeModel {
        val builder = TreeModelBuilder(myProject, grouping)

        for (section in SessionChangeSection.entries) {
            val changes = sections[section].orEmpty()
            if (changes.isEmpty()) continue

            builder.insertChanges(changes, tagNode(builder, section))
        }

        if (unversioned.isNotEmpty()) builder.setUnversioned(unversioned)

        return builder.build()
    }

    private fun tagNode(builder: TreeModelBuilder, section: SessionChangeSection): ChangesBrowserNode<*> {
        val node = com.intellij.openapi.vcs.changes.ui.TagChangesBrowserNode(
            object : ChangesBrowserNode.Tag {
                override fun toString(): String = section.title
            },
            com.intellij.ui.SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES,
            true
        )
        builder.insertSubtreeRoot(node)

        return node
    }
}
