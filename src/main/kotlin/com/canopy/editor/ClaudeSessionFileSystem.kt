package com.canopy.editor

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileListener
import com.intellij.openapi.vfs.VirtualFileSystem
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

class ClaudeSessionFileSystem : VirtualFileSystem() {

    private val files = ConcurrentHashMap<String, WeakReference<ClaudeSessionVirtualFile>>()

    fun register(file: ClaudeSessionVirtualFile): ClaudeSessionVirtualFile {
        files[file.sessionKey] = WeakReference(file)
        return file
    }

    override fun getProtocol(): String = PROTOCOL

    override fun findFileByPath(path: String): VirtualFile? {
        val ref = files[path] ?: return null
        val file = ref.get()
        if (file == null) files.remove(path) // prune collected entry
        return file
    }

    override fun refreshAndFindFileByPath(path: String): VirtualFile? = findFileByPath(path)

    override fun refresh(asynchronous: Boolean) {}

    override fun addVirtualFileListener(listener: VirtualFileListener) {}
    override fun removeVirtualFileListener(listener: VirtualFileListener) {}
    override fun deleteFile(requestor: Any?, vFile: VirtualFile) = throw UnsupportedOperationException()
    override fun moveFile(requestor: Any?, vFile: VirtualFile, newParent: VirtualFile) = throw UnsupportedOperationException()
    override fun renameFile(requestor: Any?, vFile: VirtualFile, newName: String) = throw UnsupportedOperationException()
    override fun createChildFile(requestor: Any?, vDir: VirtualFile, fileName: String): VirtualFile = throw UnsupportedOperationException()
    override fun createChildDirectory(requestor: Any?, vDir: VirtualFile, dirName: String): VirtualFile = throw UnsupportedOperationException()
    override fun copyFile(requestor: Any?, virtualFile: VirtualFile, newParent: VirtualFile, copyName: String): VirtualFile = throw UnsupportedOperationException()
    override fun isReadOnly(): Boolean = true

    companion object {
        const val PROTOCOL = "claude-session"

        fun getInstance(): ClaudeSessionFileSystem {
            return com.intellij.openapi.vfs.VirtualFileManager.getInstance()
                .getFileSystem(PROTOCOL) as? ClaudeSessionFileSystem
                ?: throw IllegalStateException("ClaudeSessionFileSystem not registered — plugin may be unloading")
        }

        fun getInstanceOrNull(): ClaudeSessionFileSystem? {
            return com.intellij.openapi.vfs.VirtualFileManager.getInstance()
                .getFileSystem(PROTOCOL) as? ClaudeSessionFileSystem
        }
    }
}
