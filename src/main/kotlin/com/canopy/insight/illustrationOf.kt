package com.canopy.insight

import com.intellij.util.IconUtil
import javax.swing.Icon

fun illustrationOf(icon: Icon): Icon = IconUtil.scale(icon, null, ILLUSTRATION_SCALE)

private const val ILLUSTRATION_SCALE = 2.5f
