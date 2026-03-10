package dev.codex.mobile.core.model

enum class ThreadStatus {
    Running,
    NeedsReview,
    Failed,
    Completed,
    Idle,
}

data class ThreadSummary(
    val id: String,
    val projectLabel: String,
    val title: String,
    val snippet: String,
    val timeLabel: String,
    val status: ThreadStatus,
    val participantInitials: List<String>,
    val progressPercent: Int? = null,
    val remainingLabel: String? = null,
)

sealed interface TimelineEntry {
    val id: String
    val timestampLabel: String

    data class UserIntent(
        override val id: String,
        override val timestampLabel: String,
        val text: String,
    ) : TimelineEntry

    data class Reasoning(
        override val id: String,
        override val timestampLabel: String,
        val summary: String,
        val steps: List<ReasoningStep>,
    ) : TimelineEntry

    data class ExecutionLog(
        override val id: String,
        override val timestampLabel: String,
        val lines: List<ExecutionLine>,
    ) : TimelineEntry

    data class ProposedChange(
        override val id: String,
        override val timestampLabel: String,
        val title: String,
        val createdCount: Int,
        val modifiedCount: Int,
    ) : TimelineEntry

    data class Message(
        override val id: String,
        override val timestampLabel: String,
        val author: String,
        val text: String,
    ) : TimelineEntry
}

data class ReasoningStep(
    val text: String,
    val completed: Boolean,
)

enum class ExecutionKind {
    Run,
    Info,
    Patch,
    Write,
    Warn,
}

data class ExecutionLine(
    val lineNumber: Int,
    val kind: ExecutionKind,
    val text: String,
)

data class ThreadDetail(
    val summary: ThreadSummary,
    val hostName: String,
    val modelLabel: String,
    val timeline: List<TimelineEntry>,
)
