package com.canopy.toolwindow

import com.canopy.util.ClaudePathEncoder
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.JBTerminalWidget
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Container
import java.nio.file.Files
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

/**
 * Reads user messages from the session JSONL and displays them as a clickable list.
 * Clicking searches the terminal text buffer for the message and scrolls to it.
 */
class MessageHistoryPanel(
    private val project: Project,
    private val widget: JBTerminalWidget,
    private val sessionIdProvider: () -> String?,
    private val workingDirProvider: () -> String?,
    parent: Disposable
) : JPanel(BorderLayout()), Disposable {

    private val log = Logger.getInstance(MessageHistoryPanel::class.java)
    private val listModel = DefaultListModel<SessionMessage>()
    private val list = JBList(listModel)
    private val gson = Gson()
    private val refreshAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)

    // Transcripts reach tens of MB, so parse only what was appended since the last pass
    // rather than the whole file every 3s. [entries] accumulates across passes.
    private val entries = mutableListOf<SessionMessage>()
    private val searchField = com.intellij.ui.SearchTextField(false)
    private val tail = com.canopy.util.JsonlTailReader()
    private var lastPath: java.nio.file.Path? = null
    @Volatile private var visible = false

    init {
        border = JBUI.Borders.empty()
        preferredSize = java.awt.Dimension(JBUI.scale(220), 0)

        list.cellRenderer = MessageCardRenderer()
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.fixedCellHeight = -1

        list.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val idx = list.locationToIndex(e.point)
                if (idx < 0) return
                if (!list.getCellBounds(idx, idx).contains(e.point)) return
                scrollToMessage(idx)
            }
        })

        searchField.textEditor.emptyText.text = "Filter messages"
        searchField.addDocumentListener(object : com.intellij.ui.DocumentAdapter() {
            override fun textChanged(event: javax.swing.event.DocumentEvent) = applyFilter()
        })

        val header = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(2, 4)
            add(searchField, BorderLayout.CENTER)
        }

        // HTML wraps to a width baked in at render time, so a drag of the splitter has to redraw.
        addComponentListener(object : java.awt.event.ComponentAdapter() {
            override fun componentResized(event: java.awt.event.ComponentEvent) {
                list.fixedCellHeight = -1
                list.revalidate()
                list.repaint()
            }
        })

        add(header, BorderLayout.NORTH)
        add(ScrollPaneFactory.createScrollPane(list), BorderLayout.CENTER)

        Disposer.register(parent, this)
        trackVisibility()
        ApplicationManager.getApplication().executeOnPooledThread { loadMessages() }
        scheduleRefresh()
    }

    private fun scheduleRefresh() {
        if (refreshAlarm.isDisposed) return
        refreshAlarm.addRequest({
            // Nothing to show while the sidebar is collapsed, and the tail read makes
            // catching up on becoming visible cheap.
            if (visible) loadMessages()
            scheduleRefresh()
        }, 3000)
    }

    /** Reload immediately when the sidebar is opened, rather than waiting out the tick. */
    private fun trackVisibility() {
        visible = isShowing
        addHierarchyListener { e ->
            if (e.changeFlags and java.awt.event.HierarchyEvent.SHOWING_CHANGED.toLong() == 0L) return@addHierarchyListener
            val nowVisible = isShowing
            val became = nowVisible && !visible
            visible = nowVisible
            if (became) ApplicationManager.getApplication().executeOnPooledThread { loadMessages() }
        }
    }

    /**
     * Parse whatever has been appended to the transcript since the last call and append the
     * new user messages to the list.
     *
     * Synchronized because the alarm thread, the visibility listener, and construction can
     * all reach it; the tail state must not interleave.
     */
    @Synchronized
    private fun loadMessages() {
        val sessionId = sessionIdProvider() ?: return
        val cwd = workingDirProvider() ?: project.basePath ?: return
        val jsonlPath = ClaudePathEncoder.projectDirCandidates(cwd)
            .map { it.resolve("$sessionId.jsonl") }
            .firstOrNull { Files.exists(it) } ?: return

        // A different transcript (session linked or rebound) invalidates the tail state.
        if (jsonlPath != lastPath) {
            lastPath = jsonlPath
            tail.reset()
            entries.clear()
        }

        // Only repaint when the *list* changed: appended lines are mostly assistant/tool
        // records, and rebuilding the model on every one would churn the EDT for nothing.
        var changed = false
        try {
            tail.consume(
                jsonlPath,
                onRestart = { entries.clear(); changed = true },
                onLine = { line -> parseUserMessage(line)?.let { entries.add(it); changed = true } }
            )
        } catch (_: Exception) {
            return
        }
        if (!changed) return

        ApplicationManager.getApplication().invokeLater { applyFilter() }
    }

    private fun parseUserMessage(line: String): SessionMessage? {
        if (line.isBlank()) return null

        return try {
            humanMessageIn(gson.fromJson(line, JsonObject::class.java), entries.size + 1)
        } catch (_: Exception) {
            null
        }
    }

    private fun scrollToMessage(listIndex: Int) {
        val entry = listModel.getElementAt(listIndex)
        val searchKey = entry.headline.take(40)

        // Count how many entries before this one share the same search key
        var targetOccurrence = 1
        for (i in 0 until listIndex) {
            if (listModel.getElementAt(i).headline.take(40) == searchKey) {
                targetOccurrence++
            }
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val textBuffer = widget.terminalTextBuffer
                var found = 0

                textBuffer.lock()
                var targetRow: Int? = null
                val historyLines: Int
                val screenLines: Int
                try {
                    historyLines = textBuffer.historyLinesCount
                    screenLines = textBuffer.screenLinesCount
                    for (row in -historyLines until screenLines) {
                        val lineText = textBuffer.getLine(row).getText().trim()
                        if (lineText.contains(searchKey)) {
                            found++
                            if (found == targetOccurrence) {
                                targetRow = row
                                break
                            }
                        }
                    }
                } finally {
                    textBuffer.unlock()
                }

                if (targetRow != null) {
                    ApplicationManager.getApplication().invokeLater {
                        doScroll(targetRow, historyLines)
                    }
                } else {
                    notFound(searchKey)
                    // Dump some buffer lines for debugging
                    val sampleLines = mutableListOf<String>()
                    textBuffer.lock()
                    try {
                        val h = textBuffer.historyLinesCount
                        val s = textBuffer.screenLinesCount
                        val start = -h
                        val end = minOf(start + 30, s)
                        for (row in start until end) {
                            val lt = textBuffer.getLine(row).getText()
                            sampleLines.add("  row=$row: [${lt.take(80)}]")
                        }
                    } finally {
                        textBuffer.unlock()
                    }
                    log.info("Message not found in terminal buffer: searchKey='$searchKey', historyLines=$historyLines, screenLines=$screenLines\nSample lines:\n${sampleLines.joinToString("\n")}")
                }
            } catch (e: Exception) {
                log.warn("Failed to search terminal buffer", e)
            }
        }
    }

    /** Silence here reads as a broken click; the message is simply older than the scrollback. */
    private fun notFound(searchKey: String) {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater

            com.intellij.notification.NotificationGroupManager.getInstance()
                .getNotificationGroup("Canopy")
                .createNotification(
                    "That message has scrolled out of the terminal's history",
                    com.intellij.notification.NotificationType.INFORMATION
                )
                .notify(project)
        }
    }

    private fun doScroll(row: Int, historyLines: Int) {
        try {
            val scrollBar = findScrollBar(widget)
            if (scrollBar != null) {
                val target = row + historyLines
                scrollBar.value = target.coerceIn(scrollBar.minimum, scrollBar.maximum - scrollBar.visibleAmount)
            } else {
                log.warn("Could not find scrollbar in terminal widget")
            }
        } catch (e: Exception) {
            log.warn("Failed to scroll terminal", e)
        }
    }

    private fun findScrollBar(container: Container): JScrollBar? {
        for (comp in container.components) {
            if (comp is JScrollBar && comp.orientation == JScrollBar.VERTICAL) return comp
            if (comp is Container) {
                val found = findScrollBar(comp)
                if (found != null) return found
            }
        }
        return null
    }

    private fun applyFilter() {
        val query = searchField.text.trim()
        val shown = synchronized(this) { entries.toList() }.filter { it.text.contains(query, ignoreCase = true) }

        listModel.clear()
        shown.forEach { listModel.addElement(it) }
    }

    override fun dispose() {}

}
