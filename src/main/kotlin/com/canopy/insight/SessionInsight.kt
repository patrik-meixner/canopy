package com.canopy.insight

import com.canopy.toolwindow.SessionMessage

enum class TaskStatus { PENDING, IN_PROGRESS, COMPLETED }

data class PlannedTask(
    val id: String,
    val subject: String,
    val description: String,
    val activeForm: String,
    val status: TaskStatus,
    val blockedBy: List<String>
)

data class ActivityEntry(
    val tool: String,
    val detail: String,
    val atMillis: Long,
    val turn: Int
)

data class TouchedFile(
    val path: String,
    val writes: Int,
    val repository: String
)

data class SessionInsight(
    val sessionId: String? = null,
    val revision: Long = 0,
    val messages: List<SessionMessage> = emptyList(),
    val activity: List<ActivityEntry> = emptyList(),
    val tasks: List<PlannedTask> = emptyList(),
    val files: List<TouchedFile> = emptyList()
)
