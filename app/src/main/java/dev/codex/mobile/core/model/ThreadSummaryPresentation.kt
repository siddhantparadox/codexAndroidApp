package dev.codex.mobile.core.model

fun ThreadSummary.workspaceFolderName(): String? {
    val trimmedPath: String = cwd.trim().trimEnd('/', '\\')
    if (trimmedPath.isBlank()) return null
    val slashIndex = trimmedPath.lastIndexOf('/')
    val backslashIndex = trimmedPath.lastIndexOf('\\')
    val separatorIndex = maxOf(slashIndex, backslashIndex)
    return trimmedPath.substring(separatorIndex + 1).ifBlank { trimmedPath }
}

fun ThreadSummary.displayMetaLabel(): String = buildList {
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

fun ThreadSourceKind.displayLabel(): String = when (this) {
    ThreadSourceKind.Cli -> "CLI"
    ThreadSourceKind.VsCode -> "VS Code"
    ThreadSourceKind.Exec -> "Exec"
    ThreadSourceKind.AppServer -> "Desktop"
    ThreadSourceKind.SubAgent -> "Sub-agent"
    ThreadSourceKind.Unknown -> "Thread"
}

fun ThreadSummary.runtimeSettingsLabel(): String? = listOfNotNull(
    currentModelName?.takeIf { it.isNotBlank() },
    currentReasoningEffort?.displayLabel(),
).joinToString(separator = " • ")
    .ifBlank { null }

