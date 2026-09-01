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
    /** Which of your prompts the agent was answering; 0 for anything before the first one. */
    val turn: Int
)

data class TouchedFile(
    val path: String,
    val writes: Int,
    val repository: String
)

/**
 * [revision] changes only when something a tab draws changed, so a tick that appended nothing but
 * tool results does not rebuild five list models and repaint them.
 */
data class SessionInsight(
    val revision: Long = 0,
    val messages: List<SessionMessage> = emptyList(),
    val activity: List<ActivityEntry> = emptyList(),
    val tasks: List<PlannedTask> = emptyList(),
    val files: List<TouchedFile> = emptyList()
)
