package com.canopy.toolwindow

import com.canopy.editor.ClaudeSessionEditor
import com.canopy.editor.ClaudeSessionVirtualFile
import com.canopy.model.ContextGroup
import com.canopy.model.ContextItem
import com.canopy.model.ContextKind
import com.canopy.model.ContextLevel
import com.canopy.model.ContextSource
import com.canopy.services.ClaudeContextService
import com.canopy.util.CanopyExecutor
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.RevealFileAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.event.MouseEvent
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

/**
 * Every directory Claude Code takes context from, and what it finds there.
 *
 * Grouped by level rather than by kind, because the question this answers is where a rule came
 * from: personal, this repository, or this session's own checkout. A worktree session has its own
 * encoded project directory, so its memory is not the memory of the parent checkout.
 */
class ClaudeContextPanel(private val project: Project, parent: Disposable) : JPanel(BorderLayout()), Disposable {

    private val rootNode = DefaultMutableTreeNode()
    private val treeModel = DefaultTreeModel(rootNode)
    private val tree = Tree(treeModel)
    private val boundTo = JBLabel().apply {
        border = JBUI.Borders.empty(4, 8)
        foreground = UIUtil.getLabelDisabledForeground()
    }

    private val hiddenKinds = HashSet<ContextKind>()
    private var groups: List<ContextGroup> = emptyList()

    @Volatile private var workingDir: String? = null

    init {
        Disposer.register(parent, this)

        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        tree.cellRenderer = ContextTreeRenderer()
        tree.emptyText.text = "Nothing loaded from these directories"

        object : DoubleClickListener() {
            override fun onDoubleClick(event: MouseEvent): Boolean = openSelected()
        }.installOn(tree)

        PopupHandler.installPopupMenu(tree, contextMenu(), "CanopyContextTree")

        add(createToolbar(), BorderLayout.NORTH)
        add(ScrollPaneFactory.createScrollPane(tree, true), BorderLayout.CENTER)
        add(boundTo, BorderLayout.SOUTH)

        project.messageBus.connect(this).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    val session = event.newFile as? ClaudeSessionVirtualFile ?: return

                    bindTo(session.workingDir)
                }
            }
        )

        bindTo(selectedSessionWorkingDir())
    }

    /** Rebinding is what makes the panel per session: a worktree tab reads a different memory directory. */
    private fun bindTo(directory: String?) {
        val effective = directory ?: project.basePath
        if (effective == workingDir && groups.isNotEmpty()) return

        workingDir = effective
        boundTo.text = effective?.let { "Session directory: $it" }.orEmpty()
        reload()
    }

    private fun selectedSessionWorkingDir(): String? =
        (FileEditorManager.getInstance(project).selectedFiles.firstOrNull() as? ClaudeSessionVirtualFile)?.workingDir

    private fun reload() {
        val directory = workingDir

        CanopyExecutor.submit {
            val scanned = ClaudeContextService.getInstance(project).scan(directory)

            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed || directory != workingDir) return@invokeLater

                groups = scanned
                rebuildTree()
            }
        }
    }

    private fun createToolbar(): JComponent {
        val group = DefaultActionGroup()
        group.add(object : AnAction("Refresh", "Re-read the context directories", AllIcons.Actions.Refresh) {
            override fun actionPerformed(event: AnActionEvent) = reload()

            override fun getActionUpdateThread() = ActionUpdateThread.EDT
        })
        group.addSeparator()
        ContextKind.entries.forEach { group.add(kindToggle(it)) }

        val toolbar = ActionManager.getInstance().createActionToolbar("CanopyContextToolbar", group, true)
        toolbar.targetComponent = this

        return toolbar.component
    }

    private fun kindToggle(kind: ContextKind) = object : ToggleAction(kind.label, "Show ${kind.label.lowercase()}", kindIcon(kind)) {
        override fun isSelected(event: AnActionEvent) = kind !in hiddenKinds

        override fun setSelected(event: AnActionEvent, state: Boolean) {
            if (state) hiddenKinds.remove(kind) else hiddenKinds.add(kind)
            rebuildTree()
        }

        override fun getActionUpdateThread() = ActionUpdateThread.EDT
    }

    private fun contextMenu() = DefaultActionGroup().apply {
        add(object : AnAction("Open", "Open the file this context comes from", AllIcons.Actions.MenuOpen) {
            override fun actionPerformed(event: AnActionEvent) {
                openSelected()
            }

            override fun update(event: AnActionEvent) {
                event.presentation.isEnabled = selectedItem() != null
            }

            override fun getActionUpdateThread() = ActionUpdateThread.EDT
        })
        add(object : AnAction("Insert into Session", "Type this skill or agent into the session terminal", AllIcons.Actions.Execute) {
            override fun actionPerformed(event: AnActionEvent) {
                selectedItem()?.let(::insert)
            }

            override fun update(event: AnActionEvent) {
                event.presentation.isEnabled = selectedItem()?.insertText?.isNotEmpty() == true
            }

            override fun getActionUpdateThread() = ActionUpdateThread.EDT
        })
        addSeparator()
        add(object : AnAction("Reveal Directory", "Show the directory this level reads from", AllIcons.Actions.MenuOpen) {
            override fun actionPerformed(event: AnActionEvent) {
                val directory = selectedSource()?.directory ?: selectedItem()?.source?.directory ?: return

                RevealFileAction.openDirectory(directory.toFile())
            }

            override fun update(event: AnActionEvent) {
                event.presentation.isEnabled = selectedSource() != null || selectedItem() != null
            }

            override fun getActionUpdateThread() = ActionUpdateThread.EDT
        })
    }

    private fun rebuildTree() {
        val expanded = expandedLabels()
        rootNode.removeAllChildren()

        for (level in ContextLevel.entries) {
            val levelGroups = groups.filter { it.source.level == level && it.source.kind !in hiddenKinds }
            if (levelGroups.isEmpty()) continue

            val levelNode = DefaultMutableTreeNode(LevelRow(level, levelGroups.first().source.directory.parent))
            for (group in levelGroups) {
                if (group.items.isEmpty()) continue

                val kindNode = DefaultMutableTreeNode(SourceRow(group.source, group.items.size))
                group.items.forEach { kindNode.add(DefaultMutableTreeNode(it)) }
                levelNode.add(kindNode)
            }
            if (levelNode.childCount == 0) {
                levelNode.add(DefaultMutableTreeNode(EmptyRow))
            }
            rootNode.add(levelNode)
        }

        treeModel.reload()
        restoreExpansion(expanded)
    }

    private fun expandedLabels(): Set<String> {
        val labels = HashSet<String>()
        for (index in 0 until rootNode.childCount) {
            val node = rootNode.getChildAt(index) as DefaultMutableTreeNode
            if (tree.isExpanded(TreePath(node.path))) labels.add(node.userObject.toString())
        }

        return labels
    }

    private fun restoreExpansion(expanded: Set<String>) {
        for (index in 0 until rootNode.childCount) {
            val node = rootNode.getChildAt(index) as DefaultMutableTreeNode
            if (expanded.isEmpty() || node.userObject.toString() in expanded) tree.expandPath(TreePath(node.path))
        }
    }

    private fun selectedNodeValue(): Any? =
        (tree.lastSelectedPathComponent as? DefaultMutableTreeNode)?.userObject

    private fun selectedItem(): ContextItem? = selectedNodeValue() as? ContextItem

    private fun selectedSource(): ContextSource? = (selectedNodeValue() as? SourceRow)?.source

    private fun openSelected(): Boolean {
        val item = selectedItem() ?: return false
        val file = LocalFileSystem.getInstance().findFileByNioFile(item.path) ?: return false

        return FileEditorManager.getInstance(project).openFile(file, true).isNotEmpty()
    }

    private fun insert(item: ContextItem) {
        if (item.insertText.isEmpty()) return
        val manager = FileEditorManager.getInstance(project)
        val session = manager.selectedFiles.firstOrNull { it is ClaudeSessionVirtualFile } ?: return
        val editor = manager.getEditors(session).filterIsInstance<ClaudeSessionEditor>().firstOrNull() ?: return

        editor.sendToTerminal(item.insertText)
    }

    override fun dispose() = Unit

    private data class LevelRow(val level: ContextLevel, val directory: Path?) {
        override fun toString(): String = level.label
    }

    private data class SourceRow(val source: ContextSource, val count: Int)

    private object EmptyRow

    private class ContextTreeRenderer : ColoredTreeCellRenderer() {
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
                is LevelRow -> renderLevel(node)
                is SourceRow -> renderSource(node)
                is ContextItem -> renderItem(node)
                EmptyRow -> append("nothing here", SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES)
            }
        }

        private fun renderLevel(node: LevelRow) {
            icon = when (node.level) {
                ContextLevel.PERSONAL -> AllIcons.General.User
                ContextLevel.PROJECT -> AllIcons.Nodes.Module
                ContextLevel.SESSION -> AllIcons.Vcs.Branch
            }
            append(node.level.label, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
            node.directory?.let { append("   ${shorten(it)}", SimpleTextAttributes.GRAYED_ATTRIBUTES) }
        }

        private fun renderSource(node: SourceRow) {
            icon = kindIcon(node.source.kind)
            append(node.source.kind.label, SimpleTextAttributes.REGULAR_ATTRIBUTES)
            append("  ${node.count}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            append("   ${shorten(node.source.directory)}", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
        }

        private fun renderItem(node: ContextItem) {
            icon = kindIcon(node.source.kind)
            append(node.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
            node.description?.let {
                val short = if (it.length > 70) it.take(67) + "…" else it
                append("   $short", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
        }
    }
}

private val KIND_ICONS = ContextKind.entries.associateWith {
    IconLoader.getIcon("/icons/kind-${it.name.lowercase()}.svg", ClaudeContextPanel::class.java)
}

internal fun kindIcon(kind: ContextKind): Icon = KIND_ICONS.getValue(kind)

/** Home-relative paths keep the row readable; the full path is one Reveal away. */
internal fun shorten(directory: Path): String {
    val home = System.getProperty("user.home")
    val text = directory.toString()

    return if (text.startsWith(home)) "~" + text.removePrefix(home) else text
}
