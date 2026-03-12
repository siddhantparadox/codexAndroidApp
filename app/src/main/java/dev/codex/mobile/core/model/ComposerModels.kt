package dev.codex.mobile.core.model

data class ComposerCatalog(
    val models: List<ComposerModelOption> = emptyList(),
    val skills: List<ComposerSkillOption> = emptyList(),
)

data class ComposerModelOption(
    val id: String,
    val displayName: String,
    val defaultReasoningEffort: ComposerReasoningEffort = ComposerReasoningEffort.Medium,
    val supportedReasoningEfforts: List<ComposerReasoningEffortOption> = emptyList(),
    val supportsPersonality: Boolean = false,
    val supportsImageInput: Boolean = true,
    val isDefault: Boolean = false,
)

enum class ComposerReasoningEffort {
    None,
    Minimal,
    Low,
    Medium,
    High,
    XHigh,
}

data class ComposerReasoningEffortOption(
    val effort: ComposerReasoningEffort,
    val description: String,
)

enum class ComposerPersonality {
    Default,
    Friendly,
    Pragmatic,
}

enum class ComposerSandboxMode {
    Default,
    ReadOnly,
    WorkspaceWrite,
    FullAccess,
}

data class ComposerSkillOption(
    val name: String,
    val path: String,
    val displayName: String,
    val shortDescription: String? = null,
)

data class ComposerImageAttachment(
    val contentUri: String,
    val label: String = "Image attached",
)

data class ThreadReplyRequest(
    val message: String = "",
    val modelId: String? = null,
    val reasoningEffort: ComposerReasoningEffort? = null,
    val personality: ComposerPersonality = ComposerPersonality.Default,
    val sandboxMode: ComposerSandboxMode = ComposerSandboxMode.Default,
    val skill: ComposerSkillOption? = null,
    val image: ComposerImageAttachment? = null,
) {
    val hasPayload: Boolean
        get() = message.isNotBlank() || skill != null || image != null
}
