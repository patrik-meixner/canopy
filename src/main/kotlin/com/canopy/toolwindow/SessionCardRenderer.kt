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
    private val getDetail: (SessionDisplay) -> String,
    private val hoveredRow: () -> Int
) : TableCellRenderer {

    /** TableView repaints the returned component's background with its selection colour, which drew
     *  a frame around the card; painting the strip explicitly is what keeps the accent on the card. */
    private val outer = object : JPanel(BorderLayout()) {
        override fun paintComponent(graphics: java.awt.Graphics) {
            graphics.color = InsightUi.panelBackground()
            graphics.fillRect(0, 0, width, height)
        }
    }
    private val island = IslandPanel(BorderLayout(JBUI.scale(10), 0))
    private val glyph = JLabel("", SwingConstants.CENTER)
    private val title = JLabel()
    private val meta = JLabel()
    private val metaCache = HashMap<String, Pair<String, String>>()

    init {
        outer.isOpaque = true
        outer.border = JBUI.Borders.empty(2, InsightUi.GAP, 2, InsightUi.GAP)
        island.border = JBUI.Borders.empty(6, 10)
        glyph.preferredSize = Dimension(JBUI.scale(20), 0)
        glyph.font = glyph.font.deriveFont(glyph.font.size2D * GLYPH_SCALE)
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

        island.islandColor = InsightUi.cardBackground(selected, hovered = row == hoveredRow())
        val (metaText, tooltip) = cachedMeta(session)
        glyph.text = glyphFor(session, attention)
        glyph.foreground = glyphColor(session, attention, selected)
        glyph.toolTipText = attention.takeIf { it != SessionAttention.None }?.name
        title.text = session.displayName
        title.foreground = InsightUi.cardForeground(selected)
        meta.text = metaText
        meta.foreground = if (selected) InsightUi.cardForeground(true) else UIUtil.getLabelDisabledForeground()
        outer.toolTipText = tooltip

        return outer
    }

    /**
     * A row is redrawn on hover, on selection, and ten times a second while an agent is working.
     * Sorting a session's repositories and formatting its age on every one of those was the whole
     * cost of drawing the list.
     */
    private fun cachedMeta(session: SessionDisplay): Pair<String, String> {
        val detail = getDetail(session)
        val key = "${session.sessionId}|${session.modified.toEpochMilli()}|$detail|${System.currentTimeMillis() / META_BUCKET_MS}"

        metaCache[key]?.let { return it }
        if (metaCache.size > META_CACHE_LIMIT) metaCache.clear()

        return (metaLine(session, detail) to tooltipFor(session)).also { metaCache[key] = it }
    }

    private fun glyphFor(session: SessionDisplay, attention: SessionAttention): String {
        // A spinner is the difference between an agent that is running and one that has stopped.
        if (attention == SessionAttention.Working || attention == SessionAttention.Compacting) {
            return spinnerFrame(System.currentTimeMillis())
        }
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
        attention == SessionAttention.WaitingForInput -> InsightUi.accent()
        getStatus(session.sessionId) == SessionStatus.OPEN_IN_PLUGIN -> InsightUi.accent()
        else -> UIUtil.getLabelDisabledForeground()
    }
}

/**
 * A name too long for the card is cut, and this is where the whole of it lives.
 *
 * An empty tooltip still opens a window, which is what put a blank grey box over the editor.
 */
internal fun tooltipFor(session: SessionDisplay): String {
    val place = session.worktreeName ?: session.projectPath
    val lines = listOfNotNull(session.displayName.takeIf { it.isNotBlank() }, place.takeIf { it.isNotBlank() })

    return if (lines.isEmpty()) "" else "<html>" + lines.joinToString("<br>") { escape(it) } + "</html>"
}

private fun escape(text: String): String =
    text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

/** Where it worked, how long ago, and what it is costing, in that order of use. */
internal fun metaLine(session: SessionDisplay, detail: String, nowMillis: Long = System.currentTimeMillis()): String {
    val age = relativeAge(session.modified.toEpochMilli(), nowMillis)

    return listOfNotNull(placesFor(session), age, detail.takeIf { it.isNotBlank() }).joinToString("   ")
}

private const val MAX_PLACES = 2

/**
 * The repositories a session actually wrote to, not the project it was launched from.
 *
 * A session started in one project routinely works in another checkout entirely, and one working
 * in a superproject usually writes to its submodules rather than to it. The list is capped so a
 * session touching six repositories cannot widen the card.
 */
internal fun placesFor(session: SessionDisplay): String? {
    val worked = session.touchedRoots.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { it.key.trimEnd('/').substringAfterLast('/') }
        .filter { it.isNotBlank() }
        .distinct()
    if (worked.isEmpty()) {
        return session.worktreeName ?: session.projectPath.substringAfterLast('/').takeIf { it.isNotBlank() }
    }

    val shown = worked.take(MAX_PLACES).joinToString(", ")
    val rest = worked.size - MAX_PLACES

    return if (rest > 0) "$shown +$rest" else shown
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
        else -> DAY_AND_MONTH.format(java.time.Instant.ofEpochMilli(atMillis))
    }
}

private const val GLYPH_SCALE = 1.35f

private const val META_BUCKET_MS = 20_000L
private const val META_CACHE_LIMIT = 400
private val DAY_AND_MONTH: java.time.format.DateTimeFormatter =
    java.time.format.DateTimeFormatter.ofPattern("d MMM").withZone(java.time.ZoneId.systemDefault())
