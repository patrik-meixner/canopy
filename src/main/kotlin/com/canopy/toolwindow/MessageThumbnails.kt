package com.canopy.toolwindow

import com.intellij.util.ui.JBUI
import java.awt.Image
import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import javax.swing.Icon
import javax.swing.ImageIcon

/**
 * Thumbnails for the images a message carried.
 *
 * Transcripts hold images inline as base64, so a session with a few screenshots is megabytes of
 * text: only a scaled thumbnail is ever kept, and only for images that have actually been drawn.
 */
object MessageThumbnails {

    private val cache = ConcurrentHashMap<String, Icon>()

    fun of(base64: String): Icon? = cache.getOrPut(key(base64)) { decode(base64) ?: EMPTY }.takeIf { it != EMPTY }

    private fun decode(base64: String): Icon? = try {
        val bytes = Base64.getDecoder().decode(base64)
        val image = ImageIO.read(ByteArrayInputStream(bytes))

        image?.let { ImageIcon(it.getScaledInstance(scaledWidth(it.width, it.height), scaledHeight(it.width, it.height), Image.SCALE_SMOOTH)) }
    } catch (_: Exception) {
        null
    }

    /** Base64 runs to hundreds of kilobytes, so the map is keyed on a digest of it, not on it. */
    private fun key(base64: String): String = "${base64.length}:${base64.take(64).hashCode()}"

    private val EMPTY: Icon = ImageIcon()
}

internal fun scaledWidth(width: Int, height: Int): Int =
    if (width >= height) MAX_THUMBNAIL else (MAX_THUMBNAIL * width / height).coerceAtLeast(1)

internal fun scaledHeight(width: Int, height: Int): Int =
    if (height > width) MAX_THUMBNAIL else (MAX_THUMBNAIL * height / width).coerceAtLeast(1)

private val MAX_THUMBNAIL = JBUI.scale(96)
