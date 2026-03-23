package dev.codex.mobile.core.model

/**
 * Returns the visible workspace folder name derived from [ThreadSummary.cwd].
 */
public fun ThreadSummary.workspaceFolderName(): String? {
    val trimmedPath: String = cwd.trim().trimEnd('/', '\\')
    if (trimmedPath.isBlank()) return null
    val slashIndex = trimmedPath.lastIndexOf('/')
    val backslashIndex = trimmedPath.lastIndexOf('\\')
    val separatorIndex = maxOf(slashIndex, backslashIndex)
    return trimmedPath.substring(separatorIndex + 1).ifBlank { trimmedPath }
}

/**
 * Returns a compact descriptive label built from thread identity metadata.
 */
public fun ThreadSummary.displayMetaLabel(): String = buildList {
    when {
        !agentRole.isNullOrBlank() -> add(agentRole)
        !agentNickname.isNullOrBlank() -> add(agentNickname)
    }
    workspaceFolderName()?.let(::add)
    gitBranch?.takeIf { it.isNotBlank() }?.let(::add)
    modelProvider
        .takeIf { it.isNotBlank() && !it.equals(other = "openai", ignoreCase = true) }
        ?.uppercase()
        ?.let(::add)
    if (ephemeral) add("EPHEMERAL")
}.joinToString(separator = " • ")
    .ifBlank { source.displayLabel() }

/**
 * Returns a human-readable label for the thread source kind.
 */
public fun ThreadSourceKind.displayLabel(): String = when (this) {
    ThreadSourceKind.Cli -> "CLI"
    ThreadSourceKind.VsCode -> "VS Code"
    ThreadSourceKind.Exec -> "Exec"
    ThreadSourceKind.AppServer -> "Desktop"
    ThreadSourceKind.SubAgent -> "Sub-agent"
    ThreadSourceKind.Unknown -> "Thread"
}

/**
 * Returns a compact model and reasoning label for the thread when available.
 */
public fun ThreadSummary.runtimeSettingsLabel(): String? = listOfNotNull(
    currentModelName?.takeIf { it.isNotBlank() },
    currentReasoningEffort?.displayLabel(),
).joinToString(separator = " • ")
    .ifBlank { null }

/**
 * Returns the best available user-visible title for the thread.
 */
public fun ThreadSummary.displayTitle(): String = name
    ?.normalizedSingleLineOrNull()
    ?: preview.normalizedSingleLineOrNull()
    ?: workspaceFolderName()?.let { folderName -> "$folderName thread" }
    ?: "Thread"

/**
 * Returns a compact metadata line for dense thread-list rows.
 */
public fun ThreadSummary.compactListMetadataLabel(): String? = listOfNotNull(
    status.compactListStatusLabel(),
    currentModelName?.normalizedSingleLineOrNull() ?: currentModelId?.normalizedSingleLineOrNull(),
    gitBranch?.normalizedSingleLineOrNull(),
).joinToString(separator = " • ")
    .ifBlank { null }

private fun ThreadStatus.compactListStatusLabel(): String? = when {
    isWaitingOnApproval -> "Needs Approval"
    isWaitingOnUserInput -> "Needs Input"
    type == ThreadStatusType.Active -> "Active"
    type == ThreadStatusType.SystemError -> "Error"
    else -> null
}

private fun String.normalizedSingleLineOrNull(): String? = lineSequence()
    .map(String::trim)
    .filter(String::isNotBlank)
    .joinToString(separator = " ")
    .ifBlank { null }

