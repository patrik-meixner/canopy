package com.canopy.toolwindow

import com.canopy.model.SessionDisplay
import com.canopy.model.SessionStatus
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel

class SessionTableModel(
    private val getStatus: (String) -> SessionStatus,
    getDetail: (SessionDisplay) -> String,
    getAttention: (SessionDisplay) -> com.canopy.model.SessionAttention,
    showWorktreeColumn: Boolean = false,
    getWorktreeStatus: ((String) -> WorktreeStatus?)? = null,
    hoveredRow: () -> Int = { -1 }
) : ListTableModel<SessionDisplay>() {
    init {
        // The worktree mode keeps real columns because it sorts by them; the session list has no
        // header at all, so a row there is free to be one card instead of two competing cells.
        val hasColumns = showWorktreeColumn || getWorktreeStatus != null
        columnInfos = if (hasColumns) {
            val cols = mutableListOf<ColumnInfo<SessionDisplay, *>>(
                StatusColumnInfo(getStatus, getAttention),
                NameColumnInfo(getDetail)
            )
            if (showWorktreeColumn) cols.add(WorktreeColumnInfo())
            if (getWorktreeStatus != null) cols.add(GitColumnInfo(getWorktreeStatus))
            cols.toTypedArray()
        } else {
            arrayOf(CardColumnInfo(getStatus, getAttention, getDetail, hoveredRow))
        }
    }
}

private class CardColumnInfo(
    getStatus: (String) -> SessionStatus,
    getAttention: (SessionDisplay) -> com.canopy.model.SessionAttention,
    getDetail: (SessionDisplay) -> String,
    hoveredRow: () -> Int
) : ColumnInfo<SessionDisplay, SessionDisplay>("Name") {

    private val renderer = SessionCardRenderer(getStatus, getAttention, getDetail, hoveredRow)
    private val terminalRenderer = TerminalCardRenderer(hoveredRow)

    override fun valueOf(item: SessionDisplay): SessionDisplay = item

    override fun getRenderer(item: SessionDisplay?): javax.swing.table.TableCellRenderer =
        if (item?.isTerminal == true) terminalRenderer else renderer
}

private class StatusColumnInfo(
    private val getStatus: (String) -> SessionStatus,
    private val getAttention: (SessionDisplay) -> com.canopy.model.SessionAttention
) : ColumnInfo<SessionDisplay, String>("") {
    override fun valueOf(item: SessionDisplay): String {
        val attention = getAttention(item)
        if (attention != com.canopy.model.SessionAttention.None) return attention.glyph

        return openStateGlyph(item)
    }

    private fun openStateGlyph(item: SessionDisplay): String = when (getStatus(item.sessionId)) {
        SessionStatus.OPEN_IN_PLUGIN -> "●"   // ● filled circle
        SessionStatus.OPEN_EXTERNALLY -> "↗"   // ↗ external arrow
        SessionStatus.AVAILABLE -> "○"          // ○ empty circle
    }

    override fun getWidth(table: javax.swing.JTable): Int = 28

    override fun getRenderer(item: SessionDisplay?): javax.swing.table.TableCellRenderer {
        return javax.swing.table.TableCellRenderer { table, value, isSelected, _, _, _ ->
            javax.swing.JLabel(value?.toString() ?: "").apply {
                horizontalAlignment = javax.swing.SwingConstants.CENTER
                font = table.font
                isOpaque = true
                background = if (isSelected) table.selectionBackground else table.background
                toolTipText = item?.let(getAttention)?.takeIf { it != com.canopy.model.SessionAttention.None }?.name
                foreground = when {
                    isSelected -> table.selectionForeground
                    item != null && getAttention(item) == com.canopy.model.SessionAttention.NeedsPermission ->
                        com.canopy.insight.InsightUi.needsAttention()
                    item != null && getAttention(item) == com.canopy.model.SessionAttention.WaitingForInput ->
                        com.canopy.insight.InsightUi.accent()
                    item != null && getStatus(item.sessionId) == SessionStatus.OPEN_IN_PLUGIN ->
                        com.intellij.ui.JBColor.namedColor("Component.focusColor", com.canopy.insight.InsightUi.accent())
                    item != null && getStatus(item.sessionId) == SessionStatus.OPEN_EXTERNALLY ->
                        com.intellij.util.ui.UIUtil.getLabelDisabledForeground()
                    else -> com.intellij.util.ui.UIUtil.getLabelDisabledForeground()
                }
            }
        }
    }
}

private class NameColumnInfo(
    private val getDetail: (SessionDisplay) -> String
) : ColumnInfo<SessionDisplay, String>("Name") {
    override fun valueOf(item: SessionDisplay): String = item.displayName

    override fun getRenderer(item: SessionDisplay?): javax.swing.table.TableCellRenderer {
        return object : com.intellij.ui.ColoredTableCellRenderer() {
            override fun customizeCellRenderer(
                table: javax.swing.JTable, value: Any?, selected: Boolean,
                hasFocus: Boolean, row: Int, column: Int
            ) {
                append(value?.toString().orEmpty())
                val detail = item?.let(getDetail).orEmpty()
                if (detail.isNotEmpty()) {
                    append("   $detail", com.intellij.ui.SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
                toolTipText = buildNameTooltip(item)
            }
        }
    }
}

private fun buildNameTooltip(item: SessionDisplay?): String? =
    item?.firstPrompt?.let(::tooltipFor)?.takeIf { it.isNotBlank() }

private const val TOOLTIP_LINES = 6
private const val TOOLTIP_LINE_LENGTH = 90

/**
 * A first prompt is often a pasted terminal banner thousands of characters wide, and Swing draws a
 * tooltip as one line unless it is told otherwise: unwrapped, that covered the window.
 */
internal fun tooltipFor(prompt: String): String {
    val lines = prompt.trim().lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .flatMap { it.chunked(TOOLTIP_LINE_LENGTH).asSequence() }
        .take(TOOLTIP_LINES)
        .toList()
    if (lines.isEmpty()) return ""

    val truncated = prompt.trim().length > lines.sumOf { it.length }

    val body = lines.joinToString("<br>") { escapeHtml(it) } + if (truncated) "<br>\u2026" else ""

    return "<html>$body</html>"
}

private fun escapeHtml(text: String): String =
    text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

private class WorktreeColumnInfo : ColumnInfo<SessionDisplay, String>("Worktree") {
    override fun valueOf(item: SessionDisplay): String = item.worktreeName ?: ""
}

/**
 * Uncommitted/unmerged state of the row's worktree: "✎3" for 3 uncommitted files, "↑2" for
 * 2 commits not in the base branch, blank when there's nothing outstanding — so a row that
 * needs attention is the only thing that draws the eye.
 *
 * Sorts on a numeric rank (uncommitted dominates) rather than the display string, so one
 * click puts the worktrees you forgot about on top. The renderer reads a cache that a
 * background sweep fills in; cells stay blank until the first sweep lands.
 */
private class GitColumnInfo(
    private val getWorktreeStatus: (String) -> WorktreeStatus?
) : ColumnInfo<SessionDisplay, Int>("Git") {

    override fun getColumnClass(): Class<*> = java.lang.Integer::class.java

    override fun valueOf(item: SessionDisplay): Int {
        val st = statusOf(item) ?: return -1
        // Uncommitted work outranks unmerged commits; the cap keeps a pathological commit
        // count from spilling into the next dirty-file bucket.
        return st.dirtyCount * 100_000 + st.ahead.coerceAtMost(99_999)
    }

    override fun getWidth(table: javax.swing.JTable): Int = 72

    private fun statusOf(item: SessionDisplay): WorktreeStatus? =
        item.worktreeName?.let { getWorktreeStatus(it) }

    override fun getRenderer(item: SessionDisplay?): javax.swing.table.TableCellRenderer {
        return javax.swing.table.TableCellRenderer { table, _, isSelected, _, _, _ ->
            val st = item?.let { statusOf(it) }
            javax.swing.JLabel(text(st)).apply {
                font = table.font
                isOpaque = true
                background = if (isSelected) table.selectionBackground else table.background
                foreground = when {
                    isSelected -> table.selectionForeground
                    st == null -> com.intellij.util.ui.UIUtil.getLabelDisabledForeground()
                    // Red is reserved for a git operation frozen mid-flight. A missing or
                    // unreadable worktree stays muted — a worktree the CLI is still creating
                    // reads as MISSING for a moment and shouldn't flash an alarm.
                    st.state in INTERRUPTED_STATES -> com.intellij.ui.JBColor.RED
                    // Uncommitted work is the thing worth noticing; unmerged commits alone
                    // are a normal in-progress state, so they stay in the muted colour.
                    st.dirty -> DIRTY_COLOR
                    else -> com.intellij.util.ui.UIUtil.getLabelDisabledForeground()
                }
                toolTipText = tooltip(st)
            }
        }
    }

    private fun text(st: WorktreeStatus?): String = when {
        st == null -> ""
        st.state == WorktreeState.REBASING -> "rebase"
        st.state == WorktreeState.MERGING -> "merge"
        st.state == WorktreeState.CHERRY_PICKING -> "pick"
        st.state == WorktreeState.DETACHED -> "detached"
        st.state == WorktreeState.MISSING -> "—"
        st.state == WorktreeState.UNKNOWN -> "?"
        else -> buildString {
            if (st.dirtyCount > 0) append("✎${st.dirtyCount}")
            if (st.ahead > 0) {
                if (isNotEmpty()) append(" ")
                append("↑${st.ahead}")
            }
        }
    }

    private fun tooltip(st: WorktreeStatus?): String? {
        if (st == null) return null
        val base = st.mainBranch ?: "the project branch"
        val lines = mutableListOf<String>()
        when (st.state) {
            WorktreeState.REBASING -> lines += "Rebase in progress"
            WorktreeState.MERGING -> lines += "Merge in progress"
            WorktreeState.CHERRY_PICKING -> lines += "Cherry-pick in progress"
            WorktreeState.DETACHED -> lines += "Detached HEAD"
            WorktreeState.MISSING -> return "Worktree directory not found"
            WorktreeState.UNKNOWN -> return "Status unavailable: ${st.errorDetail ?: "unknown error"}"
            WorktreeState.OK -> {}
        }
        if (st.dirtyCount > 0) {
            lines += "${st.dirtyCount} uncommitted file${if (st.dirtyCount > 1) "s" else ""}:"
            lines += st.dirtyFiles.take(10).map { " $it" }
            if (st.dirtyCount > 10) lines += " … and ${st.dirtyCount - 10} more"
        }
        if (st.ahead > 0) {
            lines += "${st.ahead} commit${if (st.ahead > 1) "s" else ""} not in $base"
        }
        if (st.behind > 0) {
            lines += "${st.behind} commit${if (st.behind > 1) "s" else ""} behind $base"
        }
        if (lines.isEmpty()) {
            lines += if (st.ahead == 0 && st.behind == 0) "Clean, level with $base"
                     else "Clean — nothing to merge into $base"
        }
        return lines.joinToString("<br>", prefix = "<html>", postfix = "</html>")
    }

    private companion object {
        val DIRTY_COLOR = com.canopy.insight.InsightUi.waiting()
        val INTERRUPTED_STATES = setOf(
            WorktreeState.REBASING, WorktreeState.MERGING, WorktreeState.CHERRY_PICKING
        )
    }
}


