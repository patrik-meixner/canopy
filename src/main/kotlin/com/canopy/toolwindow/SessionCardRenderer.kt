package com.canopy.toolwindow

import com.canopy.insight.InsightUi
import com.canopy.insight.IslandPanel
import com.canopy.model.SessionAttention
import com.canopy.model.SessionDisplay
import com.canopy.model.SessionStatus
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.table.TableCellRenderer

/**
 * A session as an island: what it wants, what it is called, and where and when it ran.
 *
 * One line of text at row height 26 left the state, the repository, the spend and the age of a
 * session competing for the same strip of pixels.
 */
class SessionCardRenderer(
    private val getStatus: (String) -> SessionStatus,
    private val getAttention: (SessionDisplay) -> SessionAttention,
    private val getDetail: (SessionDisplay) -> String
) : TableCellRenderer {

    /** TableView repaints the returned component's background with its selection colour, which drew
     *  a frame around the card; painting the strip explicitly is what keeps the accent on the card. */
    private val outer = object : JPanel(BorderLayout()) {
        override fun paintComponent(graphics: java.awt.Graphics) {
            graphics.color = UIUtil.getListBackground()
            graphics.fillRect(0, 0, width, height)
        }
    }
    private val island = IslandPanel(BorderLayout(JBUI.scale(10), 0))
    private val glyph = JLabel("", SwingConstants.CENTER)
    private val title = JLabel()
    private val meta = JLabel()

    init {
        outer.isOpaque = true
        outer.border = JBUI.Borders.empty(2, InsightUi.GAP, 2, InsightUi.GAP)
        island.border = JBUI.Borders.empty(6, 10)
        glyph.preferredSize = Dimension(JBUI.scale(16), 0)
        meta.font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)

        val text = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(title)
            add(meta)
        }
        island.add(glyph, BorderLayout.WEST)
        island.add(text, BorderLayout.CENTER)
        outer.add(island, BorderLayout.CENTER)
    }

    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        selected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        val session = value as? SessionDisplay ?: return outer
        val attention = getAttention(session)

        island.islandColor = InsightUi.cardBackground(selected)
        glyph.text = glyphFor(session, attention)
        glyph.foreground = glyphColor(session, attention, selected)
        glyph.toolTipText = attention.takeIf { it != SessionAttention.None }?.name
        title.text = session.displayName
        title.foreground = InsightUi.cardForeground(selected)
        meta.text = metaLine(session, getDetail(session))
        meta.foreground = if (selected) InsightUi.cardForeground(true) else UIUtil.getLabelDisabledForeground()

        return outer
    }

    private fun glyphFor(session: SessionDisplay, attention: SessionAttention): String {
        if (attention != SessionAttention.None) return attention.glyph

        return when (getStatus(session.sessionId)) {
            SessionStatus.OPEN_IN_PLUGIN -> "●"
            SessionStatus.OPEN_EXTERNALLY -> "↗"
            SessionStatus.AVAILABLE -> "○"
        }
    }

    private fun glyphColor(session: SessionDisplay, attention: SessionAttention, selected: Boolean): java.awt.Color = when {
        selected -> InsightUi.cardForeground(true)
        attention == SessionAttention.NeedsPermission -> InsightUi.needsAttention()
        attention == SessionAttention.WaitingForInput -> InsightUi.waiting()
        getStatus(session.sessionId) == SessionStatus.OPEN_IN_PLUGIN -> InsightUi.accent()
        else -> UIUtil.getLabelDisabledForeground()
    }
}

/** Where it ran, how long ago, and what it is costing, in that order of use. */
internal fun metaLine(session: SessionDisplay, detail: String, nowMillis: Long = System.currentTimeMillis()): String {
    val place = session.worktreeName ?: session.projectPath.substringAfterLast('/').takeIf { it.isNotBlank() }
    val age = relativeAge(session.modified.toEpochMilli(), nowMillis)

    return listOfNotNull(place, age, detail.takeIf { it.isNotBlank() }).joinToString("   ")
}

private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR

/** Its own arithmetic: the platform's formatter needs a running application, and this is testable. */
internal fun relativeAge(atMillis: Long, nowMillis: Long): String {
    val elapsed = nowMillis - atMillis

    return when {
        elapsed < MINUTE -> "just now"
        elapsed < HOUR -> "${elapsed / MINUTE}m ago"
        elapsed < DAY -> "${elapsed / HOUR}h ago"
        elapsed < 7 * DAY -> "${elapsed / DAY}d ago"
        else -> java.time.format.DateTimeFormatter.ofPattern("d MMM")
            .withZone(java.time.ZoneId.systemDefault())
            .format(java.time.Instant.ofEpochMilli(atMillis))
    }
}
