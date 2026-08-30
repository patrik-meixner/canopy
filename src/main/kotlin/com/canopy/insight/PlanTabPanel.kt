package com.canopy.insight

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import java.awt.Font
import javax.swing.DefaultListModel
import javax.swing.JList

/**
 * Every task the session is working through, not the handful the CLI has room to print.
 */
class PlanTabPanel(project: Project, parent: Disposable) : InsightTabPanel(project, parent) {

    private val model = DefaultListModel<PlannedTask>()
    private val list = JBList(model)

    init {
        list.cellRenderer = TaskRenderer()
        list.emptyText.text = "This session has no task list"
        list.border = JBUI.Borders.empty(4, 2)

        add(ScrollPaneFactory.createScrollPane(list, true))
    }

    override fun render(insight: SessionInsight) {
        val selected = list.selectedValue?.id

        model.clear()
        insight.tasks.forEach { model.addElement(it) }
        selected?.let { id -> (0 until model.size()).firstOrNull { model[it].id == id }?.let(list::setSelectedIndex) }
    }

    private class TaskRenderer : ColoredListCellRenderer<PlannedTask>() {
        override fun customizeCellRenderer(
            list: JList<out PlannedTask>,
            value: PlannedTask,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean
        ) {
            append("${value.status.glyph}  ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            append(value.subject, attributesFor(value.status))
            if (value.blockedBy.isNotEmpty()) {
                append("   blocked by ${value.blockedBy.joinToString(", ")}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
            toolTipText = value.description.takeIf { it.isNotBlank() }
        }

        private fun attributesFor(status: TaskStatus): SimpleTextAttributes = when (status) {
            TaskStatus.IN_PROGRESS -> SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, JBColor.foreground())
            TaskStatus.COMPLETED -> SimpleTextAttributes(SimpleTextAttributes.STYLE_STRIKEOUT, JBColor.GRAY)
            TaskStatus.PENDING -> SimpleTextAttributes.REGULAR_ATTRIBUTES
        }
    }
}
