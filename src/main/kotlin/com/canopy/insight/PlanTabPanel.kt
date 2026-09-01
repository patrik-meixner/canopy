package com.canopy.insight

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import javax.swing.JPanel

/**
 * Every task the session is working through, not the handful the CLI has room to print.
 */
class PlanTabPanel(project: Project, parent: Disposable) : InsightTabPanel(project, parent) {

    private val cards = CardListPanel(
        EmptyState(
            com.intellij.icons.AllIcons.Actions.Checked,
            "No task list yet",
            "Claude writes one when a job is worth planning, and every step it works through shows up here."
        )
    )
    private val summary = JBLabel().apply {
        border = JBUI.Borders.empty(6, 12)
        foreground = UIUtil.getLabelDisabledForeground()
    }

    init {
        background = InsightUi.panelBackground()

        add(JPanel(BorderLayout()).apply {
            background = InsightUi.panelBackground()
            add(summary, BorderLayout.WEST)
        }, BorderLayout.NORTH)
        add(cards, BorderLayout.CENTER)
    }

    override fun render(insight: SessionInsight) {
        summary.text = summaryOf(insight.tasks)
        cards.setCards(insight.tasks.map(::TaskCard), planCardKeys(insight.tasks))
    }
}

/** Reads like the CLI's own summary line, so the tab needs no explaining. */
internal fun summaryOf(tasks: List<PlannedTask>): String {
    if (tasks.isEmpty()) return ""
    val done = tasks.count { it.status == TaskStatus.COMPLETED }
    val running = tasks.count { it.status == TaskStatus.IN_PROGRESS }
    val runningText = if (running > 0) ", $running in progress" else ""

    return "$done of ${tasks.size} done$runningText"
}
