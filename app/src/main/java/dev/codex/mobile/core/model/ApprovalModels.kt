package dev.codex.mobile.core.model

enum class ApprovalKind {
    Command,
    FileChange,
    Deployment,
}

enum class ApprovalRisk {
    HighImpact,
    MediumRisk,
    LowRisk,
}

enum class ApprovalState {
    Pending,
    Approved,
    Declined,
    Archived,
    Reviewed,
}

val ApprovalState.isPending: Boolean
    get() = this == ApprovalState.Pending

data class ApprovalItem(
    val id: String,
    val threadId: String,
    val kind: ApprovalKind,
    val title: String,
    val subtitle: String,
    val detail: String,
    val risk: ApprovalRisk,
    val state: ApprovalState,
    val primaryActionLabel: String,
    val secondaryActionLabel: String,
    val tertiaryActionLabel: String? = null,
    val authorLabel: String? = null,
    val requestTimeLabel: String,
)
