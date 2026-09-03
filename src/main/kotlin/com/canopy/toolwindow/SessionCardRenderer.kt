package com.canopy.toolwindow

import com.canopy.insight.InsightUi
import com.canopy.insight.IslandPanel
import com.canopy.model.SessionAttention
import com.canopy.model.SessionDisplay
import com.canopy.model.SessionStatus
import com.intellij.icons.AllIcons
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

class SessionCardRenderer(
    private val getStatus: (String) -> SessionStatus,
    private val getAttention: (SessionDisplay) -> SessionAttention,
    private val getDetail: (SessionDisplay) -> String,
    private val hoveredRow: () -> Int,
    private val isCloseHovered: () -> Boolean = { false }
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
    private val stop = JLabel()
    private val metaCache = HashMap<String, String>()

    init {
        outer.isOpaque = true
        outer.border = JBUI.Borders.empty(2, InsightUi.GAP, 2, InsightUi.GAP)
        island.border = JBUI.Borders.empty(6, CARD_PADDING)
        glyph.preferredSize = Dimension(JBUI.scale(20), 0)
        glyph.font = glyph.font.deriveFont(glyph.font.size2D * GLYPH_SCALE)
        meta.font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)

        val text = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(title)
            add(meta)
        }
        stop.preferredSize = Dimension(CLOSE_BUTTON_SIZE, CLOSE_BUTTON_SIZE)

        island.add(glyph, BorderLayout.WEST)
        island.add(text, BorderLayout.CENTER)
        island.add(stop, BorderLayout.EAST)
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

        val isRunning = getStatus(session.sessionId) == SessionStatus.OPEN_IN_PLUGIN
        val isHovered = row == hoveredRow()
        island.islandColor = InsightUi.cardBackground(selected, hovered = isHovered, running = isRunning)
        stop.icon = if (isRunning && isHovered) {
            if (isCloseHovered()) AllIcons.Actions.CloseHovered else AllIcons.Actions.Close
        } else {
            null
        }
        val metaText = cachedMeta(session)
        glyph.text = glyphFor(session, attention)
        glyph.foreground = glyphColor(session, attention, selected)
        title.text = session.displayName
        title.foreground = InsightUi.cardForeground(selected)
        meta.text = metaText
        meta.foreground = if (selected) InsightUi.cardForeground(true) else UIUtil.getLabelDisabledForeground()
        return outer
    }

    private fun cachedMeta(session: SessionDisplay): String {
        val detail = getDetail(session)
        val key = "${session.sessionId}|${session.modified.toEpochMilli()}|$detail|${System.currentTimeMillis() / META_BUCKET_MS}"

        metaCache[key]?.let { return it }
        if (metaCache.size > META_CACHE_LIMIT) metaCache.clear()

        return metaLine(session, detail).also { metaCache[key] = it }
    }

    private fun glyphFor(session: SessionDisplay, attention: SessionAttention): String {
        val presence = when (getStatus(session.sessionId)) {
            SessionStatus.OPEN_IN_PLUGIN -> com.canopy.model.SessionPresence.OpenHere
            SessionStatus.OPEN_EXTERNALLY -> com.canopy.model.SessionPresence.OpenElsewhere
            SessionStatus.AVAILABLE -> com.canopy.model.SessionPresence.Available
        }

        return com.canopy.model.sessionGlyph(attention, presence, System.currentTimeMillis()) ?: "●"
    }

    private fun glyphColor(session: SessionDisplay, attention: SessionAttention, selected: Boolean): java.awt.Color = when {
        selected -> InsightUi.cardForeground(true)
        attention == SessionAttention.NeedsPermission -> InsightUi.needsAttention()
        attention == SessionAttention.WaitingForInput -> InsightUi.accent()
        getStatus(session.sessionId) == SessionStatus.OPEN_IN_PLUGIN -> InsightUi.accent()
        else -> UIUtil.getLabelDisabledForeground()
    }
}

internal fun tooltipFor(session: SessionDisplay): String {
    val place = session.worktreeName ?: session.projectPath
    val lines = listOfNotNull(session.displayName.takeIf { it.isNotBlank() }, place.takeIf { it.isNotBlank() })

    return if (lines.isEmpty()) "" else "<html>" + lines.joinToString("<br>") { escape(it) } + "</html>"
}

private fun escape(text: String): String =
    text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

internal fun metaLine(session: SessionDisplay, detail: String, nowMillis: Long = System.currentTimeMillis()): String {
    if (session.isDraft) return "Not started yet"
    val age = relativeAge(session.modified.toEpochMilli(), nowMillis)

    return listOfNotNull(placesFor(session), age, detail.takeIf { it.isNotBlank() }).joinToString("   ")
}

private const val MAX_PLACES = 2

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

private const val META_BUCKET_MS = 5_000L
private const val META_CACHE_LIMIT = 400
private val DAY_AND_MONTH: java.time.format.DateTimeFormatter =
    java.time.format.DateTimeFormatter.ofPattern("d MMM").withZone(java.time.ZoneId.systemDefault())
