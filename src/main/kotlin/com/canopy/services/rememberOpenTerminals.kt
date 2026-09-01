package com.canopy.services

import com.canopy.editor.ClaudeSessionVirtualFile
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project

private val log = Logger.getInstance("com.canopy.services.rememberOpenTerminals")

fun rememberOpenTerminals(project: Project, occasion: String) {
    val tabs = FileEditorManager.getInstance(project).openFiles
        .filterIsInstance<ClaudeSessionVirtualFile>()
        .map { OpenTab(it.sessionKey, it.sessionId, it.isShellSession, it.ownerSessionKey, it.baseName) }
    val remembered = rememberedTerminals(tabs)

    log.info(
        "Canopy: rememberOpenTerminals($occasion) — ${tabs.size} tab(s), " +
            "${tabs.count { it.isShell }} shell(s), remembered ${remembered.size}: " +
            tabs.joinToString { "${it.name}[shell=${it.isShell} id=${it.sessionId} owner=${it.ownerKey}]" }
    )
    OpenSessionsPersistence.getInstance(project).rememberTerminals(remembered)
}
