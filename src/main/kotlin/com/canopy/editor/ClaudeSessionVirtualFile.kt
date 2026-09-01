package com.canopy.editor

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.testFramework.LightVirtualFile
import javax.swing.Icon

private object ClaudeSessionFileType : FileType {
    private val ICON = IconLoader.getIcon("/icons/claude.svg", ClaudeSessionFileType::class.java)

    override fun getName(): String = "Claude Session"
    override fun getDefaultExtension(): String = ""
    override fun getDescription(): String = "Claude Code Session"
    override fun getIcon(): Icon = ICON
    override fun isBinary(): Boolean = true
    override fun isReadOnly(): Boolean = true
}

class ClaudeSessionVirtualFile(
    name: String,
    var sessionId: String? = null,
    val forkFrom: String? = null,
    val newWorktreeName: String? = null,
    val isShellSession: Boolean = false
) : LightVirtualFile(name, ClaudeSessionFileType, "") {

    /** Stable key used for VFS URL resolution so tabs survive drag-and-drop. */
    val sessionKey: String = sessionId
        ?: if (forkFrom != null) "fork-$forkFrom-${System.nanoTime()}" else null
        ?: newWorktreeName
        ?: if (isShellSession) "shell-${System.nanoTime()}" else null
        ?: "new-${System.nanoTime()}"

    var baseName: String = name
    var workingDir: String? = null
    var isWorktreeSession: Boolean = newWorktreeName != null
    var modelId: String? = null
    var modelName: String? = null
    var contextPercent: Double? = null
    var isUnresponsive: Boolean = false

    init {
        isWritable = false
    }

    override fun getFileSystem(): VirtualFileSystem =
        ClaudeSessionFileSystem.getInstanceOrNull() ?: super.getFileSystem()

    override fun getPath(): String = sessionKey

    override fun getUrl(): String = "${ClaudeSessionFileSystem.PROTOCOL}://$sessionKey"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ClaudeSessionVirtualFile) return false
        return sessionKey == other.sessionKey
    }

    override fun hashCode(): Int = sessionKey.hashCode()

    // Status is rendered as a fixed-size icon badge (see ClaudeSessionIconProvider), NOT
    // baked into the title text — a text glyph has a variable advance width, so toggling
    // it resized the tab and reflowed the whole strip. Returning null means "idle": the
    // badge slot is reserved but drawn empty, so the tab width is identical in every state.
    /** The same vocabulary the session list uses, so a tab and its row never disagree. */
    /**
     * The same state the session list shows, read from the same services.
     *
     * This used to read fields on the file that nothing ever wrote, so the tab silently showed
     * no state at all while the row beside it showed the truth.
     */
    fun statusGlyph(project: com.intellij.openapi.project.Project): String? {
        val id = sessionId ?: return null
        val sessions = com.canopy.services.ClaudeSessionService.getInstance(project)
        val session = sessions.getSessions().firstOrNull { it.sessionId == id }
        val attention = com.canopy.model.sessionAttentionFor(
            notifyState = com.canopy.services.ClaudeStatusService.getInstance(project).getNotifyState(id),
            isRunning = true,
            tail = session?.tail,
            idleForMillis = session?.let { System.currentTimeMillis() - it.modified.toEpochMilli() } ?: 0
        )
        val presence = when {
            isUnresponsive -> com.canopy.model.SessionPresence.Unresponsive
            sessions.isExternallyOpen(id) -> com.canopy.model.SessionPresence.OpenElsewhere
            else -> com.canopy.model.SessionPresence.OpenHere
        }

        return com.canopy.model.sessionGlyph(attention, presence, System.currentTimeMillis())
    }

    fun computeTabTitle(): String = baseName
}
