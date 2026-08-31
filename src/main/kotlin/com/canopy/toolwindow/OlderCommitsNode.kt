package com.canopy.toolwindow

import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer
import com.intellij.ui.SimpleTextAttributes

class OlderCommitsNode(private val text: String) : ChangesBrowserNode<String>(text) {

    override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
        renderer.append(text, SimpleTextAttributes.GRAYED_ATTRIBUTES)
        renderer.icon = com.intellij.icons.AllIcons.Vcs.History
    }

    override fun getTextPresentation(): String = text
}
