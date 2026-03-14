package dev.codex.mobile.core.model

enum class ThreadResultDigestKind {
    PatchReady,
    ReplyReady,
    Failed,
    Completed,
}

data class ThreadResultDigest(
    val kind: ThreadResultDigestKind,
    val title: String,
    val supportingText: String? = null,
    val fileCount: Int = 0,
    val addedLineCount: Int = 0,
    val removedLineCount: Int = 0,
)

data class InAppThreadNotification(
    val id: String,
    val threadId: String,
    val title: String,
    val message: String,
    val createdAtEpochSeconds: Long,
)

val ThreadResultDigest.displayText: String
    get() = listOfNotNull(title, supportingText)
        .joinToString(separator = " · ")
        .trim()
