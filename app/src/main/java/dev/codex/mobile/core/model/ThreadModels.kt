package dev.codex.mobile.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ThreadStatusType {
    NotLoaded,
    Idle,
    Active,
    SystemError,
}

@Serializable
data class ThreadStatus(
    val type: ThreadStatusType,
    val activeFlags: Set<String> = emptySet(),
)

val ThreadStatus.isActive: Boolean
    get() = type == ThreadStatusType.Active

val ThreadStatus.isWaitingOnApproval: Boolean
    get() = isActive && "waitingOnApproval" in activeFlags

enum class ThreadSourceKind {
    Cli,
    VsCode,
    Exec,
    AppServer,
    SubAgent,
    Unknown,
}

data class ThreadSummary(
    val id: String,
    val name: String?,
    val preview: String,
    val createdAtEpochSeconds: Long,
    val updatedAtEpochSeconds: Long,
    val modelProvider: String,
    val ephemeral: Boolean,
    val status: ThreadStatus,
    val source: ThreadSourceKind = ThreadSourceKind.Unknown,
    val cwd: String = "",
    val gitBranch: String? = null,
    val agentRole: String? = null,
    val agentNickname: String? = null,
)

@Serializable
sealed interface UserInputContent {
    @Serializable
    data class Text(
        val text: String,
        val placeholders: List<String> = emptyList(),
    ) : UserInputContent

    @Serializable
    data class Image(
        val url: String,
    ) : UserInputContent

    @Serializable
    data class LocalImage(
        val path: String,
    ) : UserInputContent

    @Serializable
    data class Skill(
        val name: String,
        val path: String,
    ) : UserInputContent

    @Serializable
    data class Mention(
        val name: String,
        val path: String,
    ) : UserInputContent
}

@Serializable
sealed interface ThreadItem {
    val id: String

    @Serializable
    data class UserMessage(
        override val id: String,
        val text: String,
        val content: List<UserInputContent> = listOf(UserInputContent.Text(text)),
    ) : ThreadItem

    @Serializable
    data class AgentMessage(
        override val id: String,
        val text: String,
        val phase: String? = null,
    ) : ThreadItem

    @Serializable
    data class Plan(
        override val id: String,
        val text: String,
    ) : ThreadItem

    @Serializable
    data class Reasoning(
        override val id: String,
        val summary: String,
        val summarySections: List<String> = if (summary.isBlank()) emptyList() else listOf(summary),
        val contentText: String = "",
    ) : ThreadItem

    @Serializable
    data class CommandExecution(
        override val id: String,
        val command: String,
        val cwd: String? = null,
        val status: ThreadItemStatus,
        val aggregatedOutput: String? = null,
        val exitCode: Int? = null,
        val commandActions: List<CommandActionHint> = emptyList(),
        val durationMs: Long? = null,
        val processId: String? = null,
        val interactions: List<String> = emptyList(),
    ) : ThreadItem

    @Serializable
    data class FileChange(
        override val id: String,
        val changes: List<FileChangeEntry>,
        val status: ThreadItemStatus,
        val toolOutput: String? = null,
    ) : ThreadItem

    @Serializable
    data class McpToolCall(
        override val id: String,
        val server: String,
        val tool: String,
        val status: ThreadItemStatus,
        val arguments: String,
        val result: String? = null,
        val errorMessage: String? = null,
        val durationMs: Long? = null,
        val progressMessages: List<String> = emptyList(),
    ) : ThreadItem

    @Serializable
    data class DynamicToolCall(
        override val id: String,
        val tool: String,
        val status: ThreadItemStatus,
        val arguments: String,
        val contentItems: List<ToolContentItem> = emptyList(),
        val success: Boolean? = null,
        val durationMs: Long? = null,
    ) : ThreadItem

    @Serializable
    data class CollabToolCall(
        override val id: String,
        val tool: String,
        val status: ThreadItemStatus,
        val senderThreadId: String,
        val receiverThreadIds: List<String>,
        val prompt: String? = null,
        val agentStates: List<CollabAgentState> = emptyList(),
    ) : ThreadItem

    @Serializable
    data class WebSearch(
        override val id: String,
        val query: String,
        val actionLabel: String? = null,
    ) : ThreadItem

    @Serializable
    data class ImageView(
        override val id: String,
        val path: String,
    ) : ThreadItem

    @Serializable
    data class ImageGeneration(
        override val id: String,
        val result: String,
        val status: String,
        val revisedPrompt: String? = null,
    ) : ThreadItem

    @Serializable
    data class ReviewMode(
        override val id: String,
        val review: String,
        val entered: Boolean,
    ) : ThreadItem

    @Serializable
    data class ContextCompaction(
        override val id: String,
    ) : ThreadItem

    @Serializable
    data class Unknown(
        override val id: String,
        val typeName: String,
        val payload: String,
    ) : ThreadItem
}

@Serializable
enum class ThreadItemStatus {
    InProgress,
    Completed,
    Failed,
    Declined,
}

@Serializable
data class CommandActionHint(
    val type: String,
    val command: String,
    val path: String? = null,
    val query: String? = null,
    val name: String? = null,
)

@Serializable
data class FileChangeEntry(
    val path: String,
    val kind: String,
    val diff: String,
)

@Serializable
sealed interface ToolContentItem {
    @Serializable
    data class Text(
        val text: String,
    ) : ToolContentItem

    @Serializable
    data class Image(
        val imageUrl: String,
    ) : ToolContentItem
}

@Serializable
data class CollabAgentState(
    val threadId: String,
    val status: String,
    val message: String? = null,
)

enum class ThreadActivityEmphasis {
    Neutral,
    Active,
    Success,
    Warning,
    Error,
}

data class ThreadActivity(
    val id: String,
    val title: String,
    val detail: String? = null,
    val emphasis: ThreadActivityEmphasis = ThreadActivityEmphasis.Neutral,
)

data class ThreadDetail(
    val summary: ThreadSummary,
    val items: List<ThreadItem>,
    val activities: List<ThreadActivity> = emptyList(),
)
