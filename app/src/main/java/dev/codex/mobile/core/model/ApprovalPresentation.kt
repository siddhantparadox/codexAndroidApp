package dev.codex.mobile.core.model

internal fun ApprovalItem.headline(): String = when (kind) {
    ApprovalKind.CommandExecution -> networkContext?.let(::networkApprovalHeadline)
        ?: command
        ?: "Command approval requested"

    ApprovalKind.FileChange -> {
        val firstPath: String = filePaths.firstOrNull() ?: "File changes pending"
        if (filePaths.size <= 1) firstPath else "$firstPath +${filePaths.size - 1} more"
    }

    ApprovalKind.Permissions -> requestedPermissions?.headline()
        ?: reason?.trim()?.takeIf(String::isNotEmpty)
        ?: "Additional permissions requested"
}

internal fun ApprovalItem.detailLines(): List<String> = when (kind) {
    ApprovalKind.CommandExecution -> buildList {
        cwd?.let { add("CWD: $it") }
        requestedPermissions
            ?.takeUnless(ApprovalPermissions::isEmpty)
            ?.summaryLines()
            ?.forEach(::add)
        add(
            if (networkContext == null) {
                "Tap to open the thread and inspect the related item."
            } else {
                "Managed network prompt for ${networkContext.host}."
            },
        )
    }

    ApprovalKind.FileChange -> buildList {
        if (filePaths.isNotEmpty()) {
            add("Paths: ${filePaths.joinToString()}")
        }
        if (grantRoot) add("Root access requested")
    }

    ApprovalKind.Permissions -> requestedPermissions
        ?.summaryLines()
        ?.takeIf(List<String>::isNotEmpty)
        ?: listOf("Codex is waiting for permission before it can continue this turn.")
}

internal fun ApprovalItem.alertMessage(): String = when (kind) {
    ApprovalKind.CommandExecution -> command
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.singleLinePreview()
        ?: requestedPermissions
            ?.compactSummary()
            ?.singleLinePreview()
        ?: reason
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.singleLinePreview()
            ?: "Review the requested command before Codex continues."

    ApprovalKind.FileChange -> filePaths.firstOrNull()
        ?.let { firstPath ->
            if (filePaths.size == 1) {
                firstPath
            } else {
                "$firstPath +${filePaths.size - 1} more"
            }
        }
        ?.singleLinePreview()
        ?: reason
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.singleLinePreview()
            ?: "Review the proposed file changes before Codex continues."

    ApprovalKind.Permissions -> requestedPermissions
        ?.compactSummary()
        ?.singleLinePreview()
        ?: reason
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.singleLinePreview()
            ?: "Review the requested sandbox access before Codex continues."
}

internal fun ApprovalItem.decisionLabel(decision: ApprovalDecision): String = when (kind) {
    ApprovalKind.Permissions -> when (decision) {
        ApprovalDecision.Accept -> "Allow Once"
        ApprovalDecision.AcceptForSession -> "Allow for Session"
        ApprovalDecision.Decline -> "Deny"
        ApprovalDecision.Cancel -> "Deny"
        is ApprovalDecision.AcceptWithExecpolicyAmendment -> decision.label
        is ApprovalDecision.ApplyNetworkPolicyAmendment -> decision.label
    }

    ApprovalKind.CommandExecution,
    ApprovalKind.FileChange,
    -> decision.label
}

internal fun ApprovalPermissions.headline(): String {
    val writeTarget: String? = writePaths.firstOrNull()
    val readTarget: String? = readPaths.firstOrNull()

    return when {
        writeTarget != null && writePaths.size == 1 -> "Allow write access to $writeTarget"
        writePaths.isNotEmpty() -> "Allow write access to ${writePaths.first()} +${writePaths.size - 1} more"
        readTarget != null && readPaths.size == 1 -> "Allow read access to $readTarget"
        readPaths.isNotEmpty() -> "Allow read access to ${readPaths.first()} +${readPaths.size - 1} more"
        networkEnabled -> "Allow additional network access"
        macOsAccessibility -> "Allow macOS accessibility access"
        macOsAutomationAll || macOsAutomationBundleIds.isNotEmpty() -> "Allow macOS automation access"
        macOsCalendar -> "Allow macOS calendar access"
        !macOsPreferences.isNullOrBlank() -> "Allow macOS preferences access"
        else -> "Additional permissions requested"
    }
}

internal fun ApprovalPermissions.summaryLines(): List<String> = buildList {
    if (readPaths.isNotEmpty()) add("Read: ${readPaths.joinToString()}")
    if (writePaths.isNotEmpty()) add("Write: ${writePaths.joinToString()}")
    if (networkEnabled) add("Network access")
    if (macOsAccessibility) add("macOS accessibility access")
    if (macOsAutomationAll) {
        add("macOS automations: all apps")
    } else if (macOsAutomationBundleIds.isNotEmpty()) {
        add("macOS automations: ${macOsAutomationBundleIds.joinToString()}")
    }
    if (macOsCalendar) add("macOS calendar access")
    macOsPreferences
        ?.takeIf(String::isNotBlank)
        ?.takeIf { value -> value != "none" }
        ?.let { value ->
            add(
                when (value) {
                    "read_only" -> "macOS preferences: read only"
                    "read_write" -> "macOS preferences: read and write"
                    else -> "macOS preferences: $value"
                },
            )
        }
}

internal fun ApprovalPermissions.compactSummary(): String = summaryLines().joinToString(separator = " • ")

internal fun String.singleLinePreview(maxLength: Int = 120): String = lineSequence()
    .map(String::trim)
    .filter(String::isNotBlank)
    .joinToString(separator = " ")
    .let { collapsed ->
        if (collapsed.length <= maxLength) {
            collapsed
        } else {
            collapsed.take(maxLength - 1).trimEnd() + "…"
        }
    }

private fun networkApprovalHeadline(networkContext: ApprovalNetworkContext): String =
    "Allow ${networkContext.protocol.displayNetworkProtocol()} access to ${networkContext.host}"

private fun String.displayNetworkProtocol(): String = when (this) {
    "http" -> "HTTP"
    "https" -> "HTTPS"
    "socks5Tcp" -> "SOCKS5 TCP"
    "socks5Udp" -> "SOCKS5 UDP"
    else -> uppercase()
}
