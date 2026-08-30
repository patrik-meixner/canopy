package com.canopy.insight

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.event.MouseEvent
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.DefaultListModel
import javax.swing.Icon
import javax.swing.JList

/**
 * What the agent has been doing, folded into runs.
 *
 * Twelve consecutive Bash rows say nothing that "Bash ×12" does not, and they bury the one Edit
 * between them; a file it wrote to opens on a double click.
 */
class ActivityTabPanel(project: Project, parent: Disposable) : InsightTabPanel(project, parent) {

    private val model = DefaultListModel<ActivityRun>()
    private val list = ViewportWidthList(model)
    private var entries: List<ActivityEntry> = emptyList()
    private var writesOnly = false

    init {
        list.cellRenderer = ActivityRenderer()
        list.emptyText.text = "Nothing recorded for this session yet"
        list.border = JBUI.Borders.empty(InsightUi.GAP, InsightUi.GAP)

        object : DoubleClickListener() {
            override fun onDoubleClick(event: MouseEvent): Boolean = openSelected()
        }.installOn(list)

        add(toolbar(), BorderLayout.NORTH)
        add(ScrollPaneFactory.createScrollPane(list, true), BorderLayout.CENTER)
    }

    override fun render(insight: SessionInsight) {
        entries = insight.activity
        rebuild()
    }

    private fun rebuild() {
        val atTop = list.firstVisibleIndex <= 0

        model.clear()
        activityRuns(entries, writesOnly).asReversed().forEach { model.addElement(it) }
        if (atTop && !model.isEmpty) list.ensureIndexIsVisible(0)
    }

    private fun toolbar(): javax.swing.JComponent {
        val group = DefaultActionGroup()
        group.add(object : ToggleAction("Writes Only", "Hide reads, searches and shell calls", AllIcons.Actions.Edit) {
            override fun isSelected(event: AnActionEvent) = writesOnly

            override fun setSelected(event: AnActionEvent, state: Boolean) {
                writesOnly = state
                rebuild()
            }

            override fun getActionUpdateThread() = ActionUpdateThread.EDT
        })

        return ActionManager.getInstance().createActionToolbar("CanopyActivity", group, true)
            .also { it.targetComponent = this }
            .component
    }

    private fun openSelected(): Boolean {
        val run = list.selectedValue ?: return false
        if (!run.detail.startsWith("/")) return false
        val file = LocalFileSystem.getInstance().findFileByNioFile(Path.of(run.detail)) ?: return false

        return FileEditorManager.getInstance(project).openFile(file, true).isNotEmpty()
    }

    private class ActivityRenderer : ColoredListCellRenderer<ActivityRun>() {
        override fun customizeCellRenderer(
            list: JList<out ActivityRun>,
            value: ActivityRun,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean
        ) {
            icon = iconFor(value.tool)
            val headline = headlineOf(value)
            append(headline.first, if (value.isWrite) writeAttributes else SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
            if (value.count > 1) append(" ×${value.count}", SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES)
            if (headline.second.isNotEmpty()) append("  ${headline.second}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            timeOf(value.lastAtMillis)?.let { append("   $it", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES) }
            toolTipText = value.detail.takeIf { it.isNotBlank() }
        }

        private val writeAttributes = SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, JBColor(0x12A57C, 0x67F7C3))
    }
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

internal fun timeOf(millis: Long): String? =
    if (millis <= 0) null else TIME.format(Instant.ofEpochMilli(millis))

/** A shell call reads as its verb; anything else reads as its tool and target. */
internal fun headlineOf(run: ActivityRun): Pair<String, String> {
    if (run.tool != "Bash") return run.tool to shortDetail(run.detail)
    val summary = summarizeCommand(run.detail)

    return summary.verb to shortDetail(summary.rest)
}

/** A path is read by its tail; a command is read by its head. */
internal fun shortDetail(detail: String): String {
    if (!detail.startsWith("/")) return detail
    val parts = detail.split("/").filter { it.isNotEmpty() }

    return if (parts.size <= 2) detail else "…/" + parts.takeLast(2).joinToString("/")
}

internal fun iconFor(tool: String): Icon = when {
    tool == "Bash" -> AllIcons.Debugger.Console
    tool == "Read" || tool == "Glob" || tool == "Grep" -> AllIcons.Actions.Search
    tool.startsWith("Task") -> AllIcons.Actions.Checked
    tool == "Agent" -> AllIcons.General.User
    tool.startsWith("mcp__") -> AllIcons.Nodes.Plugin
    else -> AllIcons.Actions.Edit
}
