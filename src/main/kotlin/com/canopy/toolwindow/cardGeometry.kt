package com.canopy.toolwindow

import com.canopy.insight.InsightUi
import com.intellij.util.ui.JBUI
import java.awt.Rectangle

const val CARD_PADDING = 10

val CLOSE_BUTTON_SIZE = JBUI.scale(16)

private val CLOSE_RIGHT_INSET = JBUI.scale(InsightUi.GAP + CARD_PADDING)

fun closeHitArea(cell: Rectangle): Rectangle = Rectangle(
    cell.x + cell.width - CLOSE_RIGHT_INSET - CLOSE_BUTTON_SIZE,
    cell.y + (cell.height - CLOSE_BUTTON_SIZE) / 2,
    CLOSE_BUTTON_SIZE,
    CLOSE_BUTTON_SIZE
)
