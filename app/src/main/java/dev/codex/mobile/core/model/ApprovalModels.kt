package dev.codex.mobile.core.model

enum class ApprovalKind {
    CommandExecution,
    FileChange,
}

enum class ApprovalDecision {
    Accept,
    AcceptForSession,
    Decline,
    Cancel,
}

val ApprovalDecision.label: String
    get() = when (this) {
        ApprovalDecision.Accept -> "Accept"
        ApprovalDecision.AcceptForSession -> "Accept for Session"
        ApprovalDecision.Decline -> "Decline"
        ApprovalDecision.Cancel -> "Cancel"
    }

data class ApprovalItem(
    val id: String,
    val threadId: String,
    val turnId: String,
    val itemId: String,
    val kind: ApprovalKind,
    val command: String? = null,
    val cwd: String? = null,
    val filePaths: List<String> = emptyList(),
    val reason: String? = null,
    val grantRoot: Boolean = false,
    val availableDecisions: List<ApprovalDecision>,
    val requestTimeLabel: String,
)
