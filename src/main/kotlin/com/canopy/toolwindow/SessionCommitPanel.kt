package com.canopy.toolwindow

import com.canopy.insight.InsightUi
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.KeyEvent
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.KeyStroke

/**
 * The Commit window's message box, where the review already is.
 *
 * A modal input dialog for a commit message hides the tree behind it, which is the one thing worth
 * looking at while writing one. This is the platform's own editor, so it brings the commit-message
 * highlighting, the history and the inspections with it - laid out the way the Commit window lays
 * it out, because that is where the habit was learned.
 */
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
        // The same ground the tree above it stands on, and the same hairline the editor draws for
        // itself, so the panel reads as the bottom of one surface rather than a strip stuck on.
        background = InsightUi.emptyBackground()
        border = JBUI.Borders.compound(
            JBUI.Borders.customLineTop(JBColor.border()),
            JBUI.Borders.empty(InsightUi.GAP)
        )
        message.preferredSize = Dimension(0, JBUI.scale(EDITOR_HEIGHT))

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
            // The one with a fill, as in the Commit window: the button that finishes the job.
            JButton("Commit and Push").apply { addActionListener { accept(AfterCommit.Push) } },
            flat("Cancel") { hidePanel() }
        )
    )

    private fun flat(text: String, onClick: () -> Unit) = JButton(text).apply {
        isContentAreaFilled = false
        isFocusPainted = false
        addActionListener { onClick() }
    }

    /** The message survives being cancelled: a commit abandoned to look at a diff is not rewritten. */
    fun ask(text: String) {
        caption.text = text
        // What the Commit window offers: the last message you wrote, ready to be edited or typed over.
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

    /** An empty message is not a commit, and git would refuse it anyway. */
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
    }
}
