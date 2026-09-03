package com.canopy.toolwindow

import com.canopy.insight.InsightUi
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.ui.JBColor
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.KeyEvent
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.KeyStroke

class SessionCommitPanel(
    private val project: Project,
    parent: Disposable,
    private val onCommit: (message: String, after: AfterCommit) -> Unit,
    private val onCancel: () -> Unit
) : JPanel(BorderLayout(0, JBUI.scale(6))) {

    // No toolbar of its own: the Commit window puts one above the editor, horizontally, next to
    // what it is about. Left to itself the editor stacks it vertically down its right edge.
    private val message = CommitMessage(project, false, false, true)
    private val lastCommitMessage: String? get() = VcsConfiguration.getInstance(project).lastNonEmptyCommitMessage

    private val caption = JBLabel().apply {
        foreground = UIUtil.getLabelDisabledForeground()
        font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
    }

    init {
        Disposer.register(parent, message)
        isVisible = false
        background = InsightUi.emptyBackground()
        border = JBUI.Borders.compound(
            JBUI.Borders.customLineTop(JBColor.border()),
            JBUI.Borders.empty(InsightUi.GAP)
        )
        message.preferredSize = Dimension(0, JBUI.scale(EDITOR_HEIGHT))
        message.border = JBUI.Borders.compound(
            RoundedLineBorder(JBColor.border(), JBUI.scale(EDITOR_ARC), 1),
            JBUI.Borders.empty(EDITOR_PADDING)
        )
        // Otherwise the field's own square line is drawn inside the rounded one.
        message.editorField.border = JBUI.Borders.empty()

        add(header(), BorderLayout.NORTH)
        add(message, BorderLayout.CENTER)
        add(buttons(), BorderLayout.SOUTH)

        registerKeyboardAction(
            { hidePanel() },
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
        )
    }

    private fun header() = JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
        isOpaque = false
        add(caption, BorderLayout.WEST)
        add(message.createToolbar(true), BorderLayout.EAST)
    }

    private fun buttons() = com.canopy.insight.rowOf(
        BUTTON_GAP,
        listOf(
            flat("Commit") { accept(AfterCommit.Stay) },
            JButton("Commit and Push").apply { addActionListener { accept(AfterCommit.Push) } },
            flat("Cancel") { hidePanel() }
        )
    )

    private fun flat(text: String, onClick: () -> Unit) = JButton(text).apply {
        isContentAreaFilled = false
        isFocusPainted = false
        addActionListener { onClick() }
    }

    fun ask(text: String) {
        caption.text = text
        if (message.text.isEmpty()) message.setText(lastCommitMessage.orEmpty())
        isVisible = true
        revalidate()
        message.requestFocusInMessage()
    }

    fun hidePanel() {
        if (!isVisible) return

        isVisible = false
        revalidate()
        onCancel()
    }

    private fun accept(after: AfterCommit) {
        val typed = message.text.trim()
        if (typed.isEmpty()) return message.requestFocusInMessage()

        VcsConfiguration.getInstance(project).saveCommitMessage(typed)
        message.setText("")
        isVisible = false
        revalidate()
        onCommit(typed, after)
    }

    override fun getPreferredSize(): Dimension =
        if (isVisible) super.getPreferredSize() else Dimension(0, 0)

    private companion object {
        const val EDITOR_HEIGHT = 96
        const val BUTTON_GAP = 6
        const val EDITOR_ARC = 10
        const val EDITOR_PADDING = 6
    }
}
