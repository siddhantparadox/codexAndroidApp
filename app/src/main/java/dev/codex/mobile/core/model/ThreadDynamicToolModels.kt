package dev.codex.mobile.core.model

enum class ThreadDynamicToolKind {
    PickPhoto,
}

data class ThreadDynamicToolRequest(
    val requestId: String,
    val threadId: String,
    val turnId: String? = null,
    val itemId: String? = null,
    val tool: String,
    val kind: ThreadDynamicToolKind,
    val prompt: String? = null,
    val arguments: String = "",
)

sealed interface ThreadDynamicToolResponse {
    data class PickPhotoSelected(
        val contentUri: String,
    ) : ThreadDynamicToolResponse

    data object Cancel : ThreadDynamicToolResponse
}
