package com.canopy.insight

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.SwingConstants

private const val DESCRIPTION_LINES = 2

class TaskCard(private val task: PlannedTask) : JPanel(BorderLayout()) {

    private val island = IslandPanel(BorderLayout(JBUI.scale(10), 0))

    init {
        isOpaque = false
        border = JBUI.Borders.empty(InsightUi.GAP / 2, InsightUi.GAP, 0, InsightUi.GAP)
        island.border = JBUI.Borders.empty(9, 11)

        island.add(statusChip(), BorderLayout.WEST)
        island.add(body(), BorderLayout.CENTER)
        add(island, BorderLayout.CENTER)

        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(event: MouseEvent) = highlight(true)

            override fun mouseExited(event: MouseEvent) = highlight(false)
        })
        toolTipText = task.description.takeIf { it.isNotBlank() }
    }

    private fun highlight(hovered: Boolean) {
        island.islandColor = if (hovered) InsightUi.hoverBackground() else InsightUi.islandBackground()
        island.repaint()
    }

    private fun statusChip() = JLabel(
        com.canopy.toolwindow.GlyphIcon(
            com.canopy.model.taskGlyph(
                isRunning = task.status == TaskStatus.IN_PROGRESS,
                isDone = task.status == TaskStatus.COMPLETED,
                nowMillis = System.currentTimeMillis()
            ),
            statusColor(task.status)
        )
    ).apply {
        verticalAlignment = SwingConstants.TOP
        preferredSize = Dimension(JBUI.scale(18), 0)
    }

    private fun body() = JPanel().apply {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        add(subject())
        description()?.let { add(it) }
        add(meta())
    }

    private fun subject() = JTextArea(task.subject).apply {
        isEditable = false
        isOpaque = false
        lineWrap = true
        wrapStyleWord = true
        isFocusable = false
        border = JBUI.Borders.empty()
        font = UIUtil.getLabelFont().deriveFont(java.awt.Font.BOLD)
        foreground = if (task.status == TaskStatus.COMPLETED) JBColor.GRAY else UIUtil.getLabelForeground()
        alignmentX = LEFT_ALIGNMENT
    }

    private fun description(): JTextArea? {
        val text = task.description.lineSequence().filter { it.isNotBlank() }.take(DESCRIPTION_LINES).joinToString(" ")
        if (text.isBlank()) return null

        return JTextArea(text).apply {
            isEditable = false
            isOpaque = false
            lineWrap = true
            wrapStyleWord = true
            isFocusable = false
            border = JBUI.Borders.emptyTop(3)
            font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
            foreground = UIUtil.getLabelDisabledForeground()
            alignmentX = LEFT_ALIGNMENT
        }
    }

    private fun meta() = JLabel(metaOf(task)).apply {
        font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
        foreground = statusColor(task.status)
        border = JBUI.Borders.emptyTop(5)
        alignmentX = LEFT_ALIGNMENT
    }

    private fun statusColor(status: TaskStatus): java.awt.Color = when (status) {
        TaskStatus.IN_PROGRESS -> InsightUi.accent()
        TaskStatus.COMPLETED -> JBColor.GRAY
        TaskStatus.PENDING -> UIUtil.getLabelDisabledForeground()
    }
}

internal fun metaOf(task: PlannedTask): String {
    val state = when (task.status) {
        TaskStatus.IN_PROGRESS -> task.activeForm.takeIf { it.isNotBlank() } ?: "In progress"
        TaskStatus.COMPLETED -> "Done"
        TaskStatus.PENDING -> "Pending"
    }
    val blocked = task.blockedBy.takeIf { it.isNotEmpty() }?.let { "  blocked by ${it.joinToString(", ")}" }.orEmpty()

    return "#${task.id}  $state$blocked"
}
