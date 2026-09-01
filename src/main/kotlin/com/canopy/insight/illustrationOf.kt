package com.canopy.insight

import com.intellij.util.IconUtil
import javax.swing.Icon

/** A 16px toolbar icon is a mark; the same icon at two and a half times the size is a picture. */
fun illustrationOf(icon: Icon): Icon = IconUtil.scale(icon, null, ILLUSTRATION_SCALE)

private const val ILLUSTRATION_SCALE = 2.5f
