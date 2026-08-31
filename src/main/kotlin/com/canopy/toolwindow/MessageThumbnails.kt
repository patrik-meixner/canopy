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

    fun of(base64: String): Icon? = cache.getOrPut(key(base64)) { decode(base64, THUMBNAIL) ?: EMPTY }.takeIf { it != EMPTY }

    /** What is already decoded, for the thread that must not decode. */
    fun cached(base64: String): Icon? = cache[key(base64)]?.takeIf { it != EMPTY }

    /**
     * A screenshot is hundreds of kilobytes of base64 and ImageIO is not fast on it. Decoding while
     * building the cards froze the IDE for seconds at a time on a session full of them.
     */
    fun decodeInBackground(base64: String, onReady: (Icon) -> Unit) {
        com.canopy.util.CanopyExecutor.submit {
            val icon = of(base64) ?: return@submit

            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater { onReady(icon) }
        }
    }

    /** Only what a popup is showing right now, so a full-size decode is never cached. */
    fun full(base64: String): Icon? = decode(base64, JBUI.scale(560))

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

    /** Base64 runs to hundreds of kilobytes, so the map is keyed on a digest of it, not on it. */
    private fun key(base64: String): String = "${base64.length}:${base64.take(64).hashCode()}"

    private val EMPTY: Icon = ImageIcon()
}

internal fun boundedWidth(width: Int, height: Int, bound: Int): Int =
    if (width >= height) bound else (bound * width / height).coerceAtLeast(1)

internal fun boundedHeight(width: Int, height: Int, bound: Int): Int =
    if (height > width) bound else (bound * height / width).coerceAtLeast(1)

private val THUMBNAIL: Int get() = JBUI.scale(72)
