package dev.codex.mobile.core.model

enum class ApprovalKind {
    CommandExecution,
    FileChange,
    Permissions,
}

data class ApprovalNetworkContext(
    val host: String,
    val protocol: String,
)

data class ApprovalPermissions(
    val readPaths: List<String> = emptyList(),
    val writePaths: List<String> = emptyList(),
    val networkEnabled: Boolean = false,
    val macOsAccessibility: Boolean = false,
    val macOsAutomationAll: Boolean = false,
    val macOsAutomationBundleIds: List<String> = emptyList(),
    val macOsCalendar: Boolean = false,
    val macOsPreferences: String? = null,
) {
    val isEmpty: Boolean
        get() = readPaths.isEmpty() &&
            writePaths.isEmpty() &&
            !networkEnabled &&
            !macOsAccessibility &&
            !macOsAutomationAll &&
            macOsAutomationBundleIds.isEmpty() &&
            !macOsCalendar &&
            macOsPreferences.isNullOrBlank()
}

sealed interface ApprovalDecision {
    data object Accept : ApprovalDecision

    data object AcceptForSession : ApprovalDecision

    data class AcceptWithExecpolicyAmendment(
        val execpolicyAmendment: List<String>,
    ) : ApprovalDecision

    data class ApplyNetworkPolicyAmendment(
        val action: String,
        val host: String,
    ) : ApprovalDecision

    data object Decline : ApprovalDecision

    data object Cancel : ApprovalDecision
}

val ApprovalDecision.label: String
    get() = when (this) {
        ApprovalDecision.Accept -> "Accept"
        ApprovalDecision.AcceptForSession -> "Accept for Session"
        is ApprovalDecision.AcceptWithExecpolicyAmendment -> "Accept and don't ask again"
        is ApprovalDecision.ApplyNetworkPolicyAmendment -> when (action) {
            "deny" -> "Deny and remember host"
            else -> "Allow and remember host"
        }
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
    val networkContext: ApprovalNetworkContext? = null,
    val requestedPermissions: ApprovalPermissions? = null,
    val reason: String? = null,
    val grantRoot: Boolean = false,
    val availableDecisions: List<ApprovalDecision>,
)
