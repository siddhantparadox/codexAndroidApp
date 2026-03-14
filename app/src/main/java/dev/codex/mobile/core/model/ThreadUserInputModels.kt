package dev.codex.mobile.core.model

data class ThreadUserInputOption(
    val label: String,
    val description: String,
)

data class ThreadUserInputQuestion(
    val id: String,
    val header: String,
    val prompt: String,
    val options: List<ThreadUserInputOption> = emptyList(),
    val isOtherAllowed: Boolean = false,
    val isSecret: Boolean = false,
)

data class ThreadUserInputRequest(
    val requestId: String,
    val threadId: String,
    val turnId: String,
    val itemId: String,
    val questions: List<ThreadUserInputQuestion>,
)

val ThreadUserInputRequest.previewText: String
    get() = questions.firstOrNull()?.prompt ?: "Input requested"
