package com.canopy.insight

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import javax.swing.DefaultListModel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.SwingConstants

/**
 * Every task the session is working through, not the handful the CLI has room to print.
 */
class PlanTabPanel(project: Project, parent: Disposable) : InsightTabPanel(project, parent) {

    private val model = DefaultListModel<PlannedTask>()
    private val list = JBList(model)

    private val summary = JBLabel().apply {
        border = JBUI.Borders.empty(6, 12)
        foreground = UIUtil.getLabelDisabledForeground()
    }

    init {
        list.cellRenderer = TaskRenderer()
        list.emptyText.text = "This session has no task list"
        list.border = JBUI.Borders.empty(InsightUi.GAP / 2, 0, InsightUi.GAP, 0)

        add(summary, BorderLayout.NORTH)
        add(ScrollPaneFactory.createScrollPane(list, true), BorderLayout.CENTER)
    }

    override fun render(insight: SessionInsight) {
        val selected = list.selectedValue?.id

        summary.text = summaryOf(insight.tasks)
        model.clear()
        insight.tasks.forEach { model.addElement(it) }
        selected?.let { id -> (0 until model.size()).firstOrNull { model[it].id == id }?.let(list::setSelectedIndex) }
    }

    private class TaskRenderer : ListCellRenderer<PlannedTask> {
        private val outer = JPanel(BorderLayout())
        private val card = IslandPanel(BorderLayout(JBUI.scale(8), 0))
        private val glyph = JBLabel("", SwingConstants.CENTER)
        private val subject = JBLabel()
        private val note = JBLabel("", SwingConstants.RIGHT)

        init {
            outer.isOpaque = true
            outer.border = JBUI.Borders.empty(InsightUi.GAP / 2, InsightUi.GAP, 0, InsightUi.GAP)
            card.border = JBUI.Borders.empty(7, 10)
            glyph.preferredSize = java.awt.Dimension(JBUI.scale(18), 0)
            note.font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)

            card.add(glyph, BorderLayout.WEST)
            card.add(subject, BorderLayout.CENTER)
            card.add(note, BorderLayout.EAST)
            outer.add(card, BorderLayout.CENTER)
        }

        override fun getListCellRendererComponent(
            list: JList<out PlannedTask>,
            value: PlannedTask,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean
        ): java.awt.Component {
            outer.background = UIUtil.getListBackground()
            card.islandColor = InsightUi.cardBackground(selected)
            glyph.text = value.status.glyph
            glyph.foreground = glyphColor(value.status, selected)
            subject.text = subjectHtml(value, list.width)
            subject.foreground = if (value.status == TaskStatus.COMPLETED && !selected) JBColor.GRAY
                else InsightUi.cardForeground(selected)
            note.text = value.blockedBy.takeIf { it.isNotEmpty() }?.let { "blocked by ${it.joinToString(", ")}" }.orEmpty()
            note.foreground = InsightUi.mutedForeground(selected)
            outer.toolTipText = value.description.takeIf { it.isNotBlank() }

            return outer
        }

        private fun subjectHtml(task: PlannedTask, listWidth: Int): String {
            val width = (listWidth - JBUI.scale(90)).coerceAtLeast(JBUI.scale(120))
            val body = task.subject.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            val styled = if (task.status == TaskStatus.COMPLETED) "<s>$body</s>" else body

            return "<html><body style='width:${width}px'>$styled</body></html>"
        }

        private fun glyphColor(status: TaskStatus, selected: Boolean): java.awt.Color = when {
            selected -> InsightUi.cardForeground(true)
            status == TaskStatus.IN_PROGRESS -> JBColor(0x12A57C, 0x67F7C3)
            status == TaskStatus.COMPLETED -> JBColor.GRAY
            else -> UIUtil.getLabelForeground()
        }
    }
}

/** Reads like the CLI's own summary line, so the tab needs no explaining. */
internal fun summaryOf(tasks: List<PlannedTask>): String {
    if (tasks.isEmpty()) return ""
    val done = tasks.count { it.status == TaskStatus.COMPLETED }
    val running = tasks.count { it.status == TaskStatus.IN_PROGRESS }
    val running_text = if (running > 0) ", $running in progress" else ""

    return "$done of ${tasks.size} done$running_text"
}
