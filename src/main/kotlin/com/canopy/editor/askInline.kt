package com.canopy.editor

import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.event.KeyEvent
import javax.swing.JPanel
import javax.swing.KeyStroke

fun askInline(over: Component?, title: String, initial: String = "", onAccept: (String) -> Unit) {
    val field = JBTextField(initial).apply {
        preferredSize = Dimension(JBUI.scale(FIELD_WIDTH), preferredSize.height)
        selectAll()
    }
    val content = JPanel(BorderLayout(0, JBUI.scale(4))).apply {
        border = JBUI.Borders.empty(8, 10)
        add(JBLabel(title).apply { foreground = UIUtil.getLabelDisabledForeground() }, BorderLayout.NORTH)
        add(field, BorderLayout.CENTER)
    }

    val popup = JBPopupFactory.getInstance()
        .createComponentPopupBuilder(content, field)
        .setRequestFocus(true)
        .setCancelOnClickOutside(true)
        .setCancelKeyEnabled(true)
        .createPopup()

    field.registerKeyboardAction(
        {
            val typed = field.text.trim()
            popup.cancel()
            if (typed.isNotEmpty()) onAccept(typed)
        },
        KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
        JPanel.WHEN_FOCUSED
    )

    if (over == null) popup.showInFocusCenter() else popup.showUnderneathOf(over)
}

private const val FIELD_WIDTH = 260
