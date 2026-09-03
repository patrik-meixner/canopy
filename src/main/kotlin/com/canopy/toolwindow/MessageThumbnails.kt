package com.canopy.toolwindow

import com.intellij.util.ui.JBUI
import java.awt.Image
import java.io.ByteArrayInputStream
import java.util.Base64
import javax.imageio.ImageIO
import javax.swing.Icon
import javax.swing.ImageIcon

object MessageThumbnails {

    private val cache = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, Icon>(REMEMBERED, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, Icon>) = size > REMEMBERED
        }
    )

    fun of(base64: String): Icon? {
        val key = key(base64)
        val known = cache[key]
        if (known != null) return known.takeIf { it != EMPTY }

        val decoded = decode(base64, THUMBNAIL) ?: EMPTY
        cache[key] = decoded

        return decoded.takeIf { it != EMPTY }
    }

    fun cached(base64: String): Icon? = cache[key(base64)]?.takeIf { it != EMPTY }

    fun decodeInBackground(base64: String, onReady: (Icon) -> Unit) {
        com.canopy.util.CanopyExecutor.submit {
            val icon = of(base64) ?: return@submit

            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater { onReady(icon) }
        }
    }

    fun fullInBackground(base64: String, onReady: (Icon) -> Unit) {
        com.canopy.util.CanopyExecutor.submit {
            val icon = decode(base64, JBUI.scale(FULL)) ?: return@submit

            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater { onReady(icon) }
        }
    }

    private fun decode(base64: String, bound: Int): Icon? = try {
        val bytes = Base64.getDecoder().decode(base64)
        val image = ImageIO.read(ByteArrayInputStream(bytes))

        image?.let {
            val width = boundedWidth(it.width, it.height, bound)
            val height = boundedHeight(it.width, it.height, bound)
            ImageIcon(it.getScaledInstance(width, height, Image.SCALE_SMOOTH))
        }
    } catch (_: Exception) {
        null
    }

    private fun key(base64: String): String = "${base64.length}:${base64.take(64).hashCode()}"

    private val EMPTY: Icon = ImageIcon()
}

internal fun boundedWidth(width: Int, height: Int, bound: Int): Int =
    if (width >= height) bound else (bound * width / height).coerceAtLeast(1)

internal fun boundedHeight(width: Int, height: Int, bound: Int): Int =
    if (height > width) bound else (bound * height / width).coerceAtLeast(1)

private val THUMBNAIL: Int get() = JBUI.scale(72)

private const val FULL = 560

private const val REMEMBERED = 200
