package com.canopy.insight

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.DefaultListModel
import javax.swing.Icon
import javax.swing.JList

/**
 * What the agent has been doing, call by call, so the answer does not have to be read off a
 * terminal that has already scrolled past it.
 */
class ActivityTabPanel(project: Project, parent: Disposable) : InsightTabPanel(project, parent) {

    private val model = DefaultListModel<ActivityEntry>()
    private val list = JBList(model)

    init {
        list.cellRenderer = ActivityRenderer()
        list.emptyText.text = "Nothing recorded for this session yet"
        list.border = JBUI.Borders.empty(4, 2)

        add(ScrollPaneFactory.createScrollPane(list, true))
    }

    override fun render(insight: SessionInsight) {
        val atBottom = list.lastVisibleIndex >= model.size() - 1

        model.clear()
        insight.activity.asReversed().forEach { model.addElement(it) }
        if (atBottom && !model.isEmpty) list.ensureIndexIsVisible(0)
    }

    private class ActivityRenderer : ColoredListCellRenderer<ActivityEntry>() {
        override fun customizeCellRenderer(
            list: JList<out ActivityEntry>,
            value: ActivityEntry,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean
        ) {
            icon = iconFor(value.tool)
            append(value.tool, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
            if (value.detail.isNotEmpty()) append("  ${value.detail}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            timeOf(value.atMillis)?.let { append("   $it", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES) }
            toolTipText = value.detail.takeIf { it.isNotBlank() }
        }
    }
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

internal fun timeOf(millis: Long): String? =
    if (millis <= 0) null else TIME.format(Instant.ofEpochMilli(millis))

internal fun iconFor(tool: String): Icon = when {
    tool == "Bash" -> AllIcons.Debugger.Console
    tool == "Read" || tool == "Glob" || tool == "Grep" -> AllIcons.Actions.Search
    tool.startsWith("Task") -> AllIcons.Actions.Checked
    tool == "Agent" -> AllIcons.General.User
    tool.startsWith("mcp__") -> AllIcons.Nodes.Plugin
    else -> AllIcons.Actions.Edit
}
