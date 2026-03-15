package dev.codex.mobile.core.model

data class ThreadUserInputOption(
    val value: String,
    val label: String,
    val description: String = "",
)

data class ThreadUserInputQuestion(
    val id: String,
    val header: String,
    val prompt: String,
    val options: List<ThreadUserInputOption> = emptyList(),
    val isOtherAllowed: Boolean = false,
    val isSecret: Boolean = false,
)

enum class ThreadUserInputTextFormat {
    PlainText,
    Email,
    Uri,
    Date,
    DateTime,
}

sealed interface ThreadUserInputFieldKind {
    data class Text(
        val defaultValue: String? = null,
        val format: ThreadUserInputTextFormat = ThreadUserInputTextFormat.PlainText,
        val minLength: Int? = null,
        val maxLength: Int? = null,
    ) : ThreadUserInputFieldKind

    data class Number(
        val defaultValue: Double? = null,
        val isInteger: Boolean = false,
        val minimum: Double? = null,
        val maximum: Double? = null,
    ) : ThreadUserInputFieldKind

    data class Toggle(
        val defaultValue: Boolean? = null,
    ) : ThreadUserInputFieldKind

    data class SingleSelect(
        val options: List<ThreadUserInputOption>,
        val defaultValue: String? = null,
    ) : ThreadUserInputFieldKind

    data class MultiSelect(
        val options: List<ThreadUserInputOption>,
        val defaultValues: List<String> = emptyList(),
        val minItems: Int? = null,
        val maxItems: Int? = null,
    ) : ThreadUserInputFieldKind
}

data class ThreadUserInputField(
    val key: String,
    val label: String,
    val description: String? = null,
    val required: Boolean = false,
    val kind: ThreadUserInputFieldKind,
)

sealed interface ThreadUserInputPayload {
    data class ToolQuestions(
        val questions: List<ThreadUserInputQuestion>,
    ) : ThreadUserInputPayload

    data class McpForm(
        val serverName: String,
        val message: String,
        val fields: List<ThreadUserInputField>,
    ) : ThreadUserInputPayload

    data class McpUrl(
        val serverName: String,
        val message: String,
        val url: String,
        val elicitationId: String,
    ) : ThreadUserInputPayload
}

data class ThreadUserInputRequest(
    val requestId: String,
    val threadId: String,
    val turnId: String?,
    val itemId: String?,
    val payload: ThreadUserInputPayload,
)

sealed interface ThreadUserInputAnswer {
    data class TextList(
        val values: List<String>,
    ) : ThreadUserInputAnswer

    data class BooleanValue(
        val value: Boolean,
    ) : ThreadUserInputAnswer
}

sealed interface ThreadUserInputResponse {
    data class Accept(
        val answers: Map<String, ThreadUserInputAnswer> = emptyMap(),
    ) : ThreadUserInputResponse

    data object Decline : ThreadUserInputResponse

    data object Cancel : ThreadUserInputResponse
}

val ThreadUserInputRequest.previewText: String
    get() = when (val currentPayload = payload) {
        is ThreadUserInputPayload.ToolQuestions -> currentPayload.questions.firstOrNull()?.prompt ?: "Input requested"
        is ThreadUserInputPayload.McpForm -> currentPayload.message.ifBlank { "Input requested" }
        is ThreadUserInputPayload.McpUrl -> currentPayload.message.ifBlank { currentPayload.url }
    }
