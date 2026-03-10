package dev.codex.mobile.core.model

enum class ThreadStatusType {
    NotLoaded,
    Idle,
    Active,
    SystemError,
}

data class ThreadStatus(
    val type: ThreadStatusType,
    val activeFlags: Set<String> = emptySet(),
)

val ThreadStatus.isActive: Boolean
    get() = type == ThreadStatusType.Active

val ThreadStatus.isWaitingOnApproval: Boolean
    get() = isActive && "waitingOnApproval" in activeFlags

data class ThreadSummary(
    val id: String,
    val name: String?,
    val preview: String,
    val createdAtEpochSeconds: Long,
    val updatedAtEpochSeconds: Long,
    val modelProvider: String,
    val ephemeral: Boolean,
    val status: ThreadStatus,
)

sealed interface ThreadItem {
    val id: String
    val timestampLabel: String

    data class UserMessage(
        override val id: String,
        override val timestampLabel: String,
        val text: String,
    ) : ThreadItem

    data class AgentMessage(
        override val id: String,
        override val timestampLabel: String,
        val text: String,
        val phase: String? = null,
    ) : ThreadItem

    data class Plan(
        override val id: String,
        override val timestampLabel: String,
        val text: String,
    ) : ThreadItem

    data class Reasoning(
        override val id: String,
        override val timestampLabel: String,
        val summary: String,
    ) : ThreadItem

    data class CommandExecution(
        override val id: String,
        override val timestampLabel: String,
        val command: String,
        val cwd: String? = null,
        val status: ThreadItemStatus,
        val aggregatedOutput: String? = null,
        val exitCode: Int? = null,
    ) : ThreadItem

    data class FileChange(
        override val id: String,
        override val timestampLabel: String,
        val changes: List<FileChangeEntry>,
        val status: ThreadItemStatus,
    ) : ThreadItem
}

enum class ThreadItemStatus {
    InProgress,
    Completed,
    Failed,
    Declined,
}

data class FileChangeEntry(
    val path: String,
    val kind: String,
    val diff: String,
)

data class ThreadDetail(
    val summary: ThreadSummary,
    val items: List<ThreadItem>,
)
