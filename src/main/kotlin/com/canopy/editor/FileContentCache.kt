package com.canopy.editor

import java.nio.file.Files
import java.nio.file.Path

class FileContentCache {

    private var headSha: String? = null
    private val atHead = HashMap<String, String>()
    private val onDisk = HashMap<String, Read>()

    @Synchronized
    fun atHead(sha: String, path: String, read: () -> String): String {
        if (sha != headSha) {
            headSha = sha
            atHead.clear()
        }

        return atHead.getOrPut(path, read)
    }

    fun onDisk(path: String): String? {
        val file = Path.of(path)
        val modified = runCatching { Files.getLastModifiedTime(file).toMillis() }.getOrNull() ?: return null
        val size = runCatching { Files.size(file) }.getOrNull() ?: return null
        synchronized(this) {
            onDisk[path]?.takeIf { it.modified == modified && it.size == size }?.let { return it.content }
        }

        val content = runCatching { Files.readString(file) }.getOrNull() ?: return null
        synchronized(this) { onDisk[path] = Read(modified, size, content) }

        return content
    }

    @Synchronized
    fun retainOnly(paths: Set<String>) {
        atHead.keys.retainAll(paths)
        onDisk.keys.retainAll(paths)
    }

    private class Read(val modified: Long, val size: Long, val content: String)
}
