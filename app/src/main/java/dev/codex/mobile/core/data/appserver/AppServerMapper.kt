package dev.codex.mobile.core.data.appserver

import dev.codex.mobile.core.model.AccountState
import dev.codex.mobile.core.model.AccountStatus
import dev.codex.mobile.core.model.ApprovalDecision
import dev.codex.mobile.core.model.ApprovalItem
import dev.codex.mobile.core.model.ApprovalKind
import dev.codex.mobile.core.model.ComposerCatalog
import dev.codex.mobile.core.model.ComposerModelOption
import dev.codex.mobile.core.model.ComposerReasoningEffort
import dev.codex.mobile.core.model.ComposerReasoningEffortOption
import dev.codex.mobile.core.model.ComposerSkillOption
import dev.codex.mobile.core.model.CollabAgentState
import dev.codex.mobile.core.model.CommandActionHint
import dev.codex.mobile.core.model.FileChangeEntry
import dev.codex.mobile.core.model.ThreadDetail
import dev.codex.mobile.core.model.ThreadItem
import dev.codex.mobile.core.model.ThreadItemStatus
import dev.codex.mobile.core.model.ThreadSourceKind
import dev.codex.mobile.core.model.ThreadStatus
import dev.codex.mobile.core.model.ThreadStatusType
import dev.codex.mobile.core.model.ThreadSummary
import dev.codex.mobile.core.model.ToolContentItem
import dev.codex.mobile.core.model.ThreadUserInputOption
import dev.codex.mobile.core.model.ThreadUserInputQuestion
import dev.codex.mobile.core.model.ThreadUserInputRequest
import dev.codex.mobile.core.model.UserInputContent
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

internal data class ThreadSessionSettings(
    val modelId: String? = null,
    val reasoningEffort: ComposerReasoningEffort? = null,
)

internal fun catalogFromResponses(
    modelResponse: JsonObject,
    skillsResponse: JsonObject,
): ComposerCatalog = ComposerCatalog(
    models = modelResponse.arrayAt("data")
        ?.map { model -> model.jsonObject.toComposerModelOption() }
        .orEmpty()
        .sortedWith(compareByDescending<ComposerModelOption> { it.isDefault }.thenBy { it.displayName.lowercase() }),
    skills = skillsResponse.arrayAt("data")
        ?.flatMap { entry ->
            entry.jsonObject.arrayAt("skills")
                ?.map { skill -> skill.jsonObject.toComposerSkillOption() }
                .orEmpty()
        }
        .orEmpty()
        .distinctBy { it.path }
        .sortedBy { it.displayName.lowercase() },
)

internal fun JsonObject.toAccountState(): AccountState {
    val account = objectAt("account")
    val requiresOpenaiAuth = boolean("requiresOpenaiAuth") ?: false

    if (account == null) {
        return AccountState(
            status = if (requiresOpenaiAuth) AccountStatus.RequiresLogin else AccountStatus.Unknown,
            requiresOpenaiAuth = requiresOpenaiAuth,
        )
    }

    return when (account.string("type")) {
        "apiKey" -> AccountState(
            status = AccountStatus.ApiKey,
            requiresOpenaiAuth = requiresOpenaiAuth,
        )

        "chatgpt" -> AccountState(
            status = AccountStatus.ChatGpt,
            email = account.string("email"),
            planType = account.string("planType"),
            requiresOpenaiAuth = requiresOpenaiAuth,
        )

        else -> AccountState(requiresOpenaiAuth = requiresOpenaiAuth)
    }
}

internal fun JsonObject.toThreadSummary(): ThreadSummary = ThreadSummary(
    id = requireNotNull(string("id")),
    name = string("name"),
    preview = string("preview").orEmpty(),
    createdAtEpochSeconds = long("createdAt") ?: 0L,
    updatedAtEpochSeconds = long("updatedAt") ?: 0L,
    modelProvider = string("modelProvider").orEmpty(),
    ephemeral = boolean("ephemeral") ?: false,
    status = requireNotNull(objectAt("status")).toThreadStatus(),
    source = elementAt("source").toThreadSourceKind(),
    cwd = string("cwd").orEmpty(),
    gitBranch = objectAt("gitInfo")?.string("branch"),
    agentRole = string("agentRole"),
    agentNickname = string("agentNickname"),
)

internal fun JsonObject.toThreadDetail(): ThreadDetail = ThreadDetail(
    summary = toThreadSummary(),
    items = arrayAt("turns")
        ?.flatMap { turn -> turn.jsonObject.arrayAt("items").orEmpty().map { it.jsonObject.toThreadItem() } }
        .orEmpty(),
)

internal fun JsonObject.toThreadSessionSettings(): ThreadSessionSettings? {
    val modelId = string("model")
    val reasoningEffort = string("reasoningEffort").toNullableComposerReasoningEffort()
    if (modelId == null && reasoningEffort == null) return null
    return ThreadSessionSettings(
        modelId = modelId,
        reasoningEffort = reasoningEffort,
    )
}

internal fun JsonObject.extractActiveTurnId(): String? = arrayAt("turns")
    ?.map { it.jsonObject }
    ?.lastOrNull { turn -> turn.string("status") == "inProgress" }
    ?.string("id")

internal fun JsonObject.toApprovalItem(requestId: JsonPrimitive): ApprovalItem? = when (string("method")) {
    "item/commandExecution/requestApproval" -> {
        val params = requireNotNull(objectAt("params"))
        ApprovalItem(
            id = requestId.content,
            threadId = requireNotNull(params.string("threadId")),
            turnId = requireNotNull(params.string("turnId")),
            itemId = requireNotNull(params.string("itemId")),
            kind = ApprovalKind.CommandExecution,
            command = params.string("command"),
            cwd = params.string("cwd"),
            reason = params.string("reason"),
            availableDecisions = params.availableCommandDecisions(),
        )
    }

    "item/fileChange/requestApproval" -> {
        val params = requireNotNull(objectAt("params"))
        ApprovalItem(
            id = requestId.content,
            threadId = requireNotNull(params.string("threadId")),
            turnId = requireNotNull(params.string("turnId")),
            itemId = requireNotNull(params.string("itemId")),
            kind = ApprovalKind.FileChange,
            reason = params.string("reason"),
            grantRoot = params.string("grantRoot") != null,
            availableDecisions = listOf(
                ApprovalDecision.Accept,
                ApprovalDecision.AcceptForSession,
                ApprovalDecision.Decline,
                ApprovalDecision.Cancel,
            ),
        )
    }

    else -> null
}

internal fun JsonObject.toThreadUserInputRequest(requestId: JsonPrimitive): ThreadUserInputRequest? = when (string("method")) {
    "item/tool/requestUserInput" -> {
        val params = requireNotNull(objectAt("params"))
        ThreadUserInputRequest(
            requestId = requestId.content,
            threadId = requireNotNull(params.string("threadId")),
            turnId = requireNotNull(params.string("turnId")),
            itemId = requireNotNull(params.string("itemId")),
            questions = params.arrayAt("questions")
                ?.mapNotNull { question -> question.jsonObject.toThreadUserInputQuestion() }
                .orEmpty(),
        )
    }

    else -> null
}

internal fun JsonObject.toThreadStatus(): ThreadStatus = when (string("type")) {
    "active" -> ThreadStatus(
        type = ThreadStatusType.Active,
        activeFlags = arrayAt("activeFlags")
            ?.map { it.jsonPrimitive.content }
            ?.toSet()
            .orEmpty(),
    )

    "idle" -> ThreadStatus(type = ThreadStatusType.Idle)
    "systemError" -> ThreadStatus(type = ThreadStatusType.SystemError)
    else -> ThreadStatus(type = ThreadStatusType.NotLoaded)
}

internal fun JsonObject.toComposerModelOption(): ComposerModelOption = ComposerModelOption(
    id = string("model") ?: requireNotNull(string("id")),
    displayName = string("displayName") ?: string("model").orEmpty(),
    defaultReasoningEffort = string("defaultReasoningEffort").toComposerReasoningEffort(),
    supportedReasoningEfforts = arrayAt("supportedReasoningEfforts")
        ?.mapNotNull { item ->
            item.jsonObject.string("reasoningEffort")
                ?.let { effort ->
                    ComposerReasoningEffortOption(
                        effort = effort.toComposerReasoningEffort(),
                        description = item.jsonObject.string("description").orEmpty(),
                    )
                }
        }
        .orEmpty(),
    supportsPersonality = boolean("supportsPersonality") ?: false,
    supportsImageInput = arrayAt("inputModalities")
        ?.map { it.jsonPrimitive.content }
        ?.contains("image")
        ?: true,
    isDefault = boolean("isDefault") ?: false,
)

internal fun JsonObject.toComposerSkillOption(): ComposerSkillOption {
    val interfaceObject = objectAt("interface")
    return ComposerSkillOption(
        name = requireNotNull(string("name")),
        path = requireNotNull(string("path")),
        displayName = interfaceObject?.string("displayName")
            ?: string("name")
            .orEmpty(),
        shortDescription = interfaceObject?.string("shortDescription")
            ?: interfaceObject?.string("defaultPrompt")
            ?: string("description"),
    )
}

internal fun JsonObject.toThreadItem(): ThreadItem = when (string("type")) {
    "userMessage" -> {
        val content = arrayAt("content").toUserInputContents()
        ThreadItem.UserMessage(
            id = requireThreadItemId(),
            text = content.toUserInputText(),
            content = content,
        )
    }

    "agentMessage" -> ThreadItem.AgentMessage(
        id = requireThreadItemId(),
        text = string("text").orEmpty(),
        phase = string("phase"),
    )

    "plan" -> ThreadItem.Plan(
        id = requireThreadItemId(),
        text = string("text").orEmpty(),
    )

    "reasoning" -> {
        val summarySections = arrayAt("summary")
            ?.map { it.jsonPrimitive.content }
            .orEmpty()
        val contentText = arrayAt("content")
            ?.map { it.jsonPrimitive.content }
            ?.joinToString(separator = "\n\n")
            .orEmpty()
        ThreadItem.Reasoning(
            id = requireThreadItemId(),
            summary = summarySections.joinToString(separator = "\n").ifBlank { contentText },
            summarySections = summarySections,
            contentText = contentText,
        )
    }

    "commandExecution" -> ThreadItem.CommandExecution(
        id = requireThreadItemId(),
        command = string("command").orEmpty(),
        cwd = string("cwd"),
        status = string("status").toThreadItemStatus(),
        aggregatedOutput = string("aggregatedOutput"),
        exitCode = int("exitCode"),
        commandActions = arrayAt("commandActions")
            ?.mapNotNull { action -> action.jsonObject.toCommandActionHint() }
            .orEmpty(),
        durationMs = long("durationMs"),
        processId = string("processId"),
    )

    "fileChange" -> ThreadItem.FileChange(
        id = requireThreadItemId(),
        changes = arrayAt("changes")
            ?.map { change -> change.jsonObject.toFileChangeEntry() }
            .orEmpty(),
        status = string("status").toThreadItemStatus(),
    )

    "mcpToolCall" -> ThreadItem.McpToolCall(
        id = requireThreadItemId(),
        server = string("server").orEmpty(),
        tool = string("tool").orEmpty(),
        status = string("status").toThreadItemStatus(),
        arguments = elementAt("arguments")?.toDisplayJson().orEmpty(),
        result = objectAt("result")?.toDisplayJson(),
        errorMessage = objectAt("error")?.string("message"),
        durationMs = long("durationMs"),
    )

    "dynamicToolCall" -> ThreadItem.DynamicToolCall(
        id = requireThreadItemId(),
        tool = string("tool").orEmpty(),
        status = string("status").toThreadItemStatus(),
        arguments = elementAt("arguments")?.toDisplayJson().orEmpty(),
        contentItems = arrayAt("contentItems")
            ?.mapNotNull { item -> item.jsonObject.toToolContentItemOrNull() }
            .orEmpty(),
        success = boolean("success"),
        durationMs = long("durationMs"),
    )

    "collabToolCall", "collabAgentToolCall" -> ThreadItem.CollabToolCall(
        id = requireThreadItemId(),
        tool = string("tool").orEmpty(),
        status = string("status").toThreadItemStatus(),
        senderThreadId = string("senderThreadId").orEmpty(),
        receiverThreadIds = arrayAt("receiverThreadIds")
            ?.map { it.jsonPrimitive.content }
            .orEmpty(),
        prompt = string("prompt"),
        agentStates = objectAt("agentsStates")
            ?.entries
            ?.map { (threadId, value) ->
                val state = value.jsonObject
                CollabAgentState(
                    threadId = threadId,
                    status = state.string("status").orEmpty(),
                    message = state.string("message"),
                )
            }
            .orEmpty(),
    )

    "webSearch" -> ThreadItem.WebSearch(
        id = requireThreadItemId(),
        query = string("query").orEmpty(),
        actionLabel = objectAt("action")?.toWebSearchActionLabel(),
    )

    "imageView" -> ThreadItem.ImageView(
        id = requireThreadItemId(),
        path = string("path").orEmpty(),
    )

    "imageGeneration" -> ThreadItem.ImageGeneration(
        id = requireThreadItemId(),
        result = string("result").orEmpty(),
        status = string("status").orEmpty(),
        revisedPrompt = string("revisedPrompt"),
    )

    "enteredReviewMode" -> ThreadItem.ReviewMode(
        id = requireThreadItemId(),
        review = string("review").orEmpty(),
        entered = true,
    )

    "exitedReviewMode" -> ThreadItem.ReviewMode(
        id = requireThreadItemId(),
        review = string("review").orEmpty(),
        entered = false,
    )

    "contextCompaction" -> ThreadItem.ContextCompaction(
        id = requireThreadItemId(),
    )

    else -> ThreadItem.Unknown(
        id = requireThreadItemId(),
        typeName = string("type").orEmpty(),
        payload = toDisplayJson(),
    )
}

internal fun commandApprovalDecisionPayload(decision: ApprovalDecision): JsonPrimitive = when (decision) {
    ApprovalDecision.Accept -> JsonPrimitive("accept")
    ApprovalDecision.AcceptForSession -> JsonPrimitive("acceptForSession")
    ApprovalDecision.Decline -> JsonPrimitive("decline")
    ApprovalDecision.Cancel -> JsonPrimitive("cancel")
}

internal fun fileChangeApprovalDecisionPayload(decision: ApprovalDecision): JsonPrimitive = when (decision) {
    ApprovalDecision.Accept -> JsonPrimitive("accept")
    ApprovalDecision.AcceptForSession -> JsonPrimitive("acceptForSession")
    ApprovalDecision.Decline -> JsonPrimitive("decline")
    ApprovalDecision.Cancel -> JsonPrimitive("cancel")
}

internal fun toolRequestUserInputResponsePayload(
    answers: Map<String, List<String>>,
): JsonObject = buildJsonObject {
    putJsonObject("answers") {
        answers.forEach { (questionId, values) ->
            putJsonObject(questionId) {
                put(
                    "answers",
                    buildJsonArray {
                        values.filter { value -> value.isNotBlank() }
                            .forEach { value -> add(JsonPrimitive(value)) }
                    },
                )
            }
        }
    }
}

private fun JsonObject.toThreadUserInputQuestion(): ThreadUserInputQuestion? {
    val id = string("id") ?: return null
    val header = string("header") ?: return null
    val prompt = string("question") ?: return null
    return ThreadUserInputQuestion(
        id = id,
        header = header,
        prompt = prompt,
        options = arrayAt("options")
            ?.mapNotNull { option -> option.jsonObject.toThreadUserInputOption() }
            .orEmpty(),
        isOtherAllowed = boolean("isOther") ?: false,
        isSecret = boolean("isSecret") ?: false,
    )
}

private fun JsonObject.toThreadUserInputOption(): ThreadUserInputOption? {
    val label = string("label") ?: return null
    val description = string("description") ?: return null
    return ThreadUserInputOption(
        label = label,
        description = description,
    )
}

private fun JsonObject.toCommandActionHint(): CommandActionHint? {
    val type = string("type") ?: return null
    return CommandActionHint(
        type = type,
        command = string("command").orEmpty(),
        path = string("path"),
        query = string("query"),
        name = string("name"),
    )
}

private fun JsonObject.toFileChangeEntry(): FileChangeEntry = FileChangeEntry(
    path = string("path").orEmpty(),
    kind = objectAt("kind").toPatchKindLabel(),
    diff = string("diff").orEmpty(),
)

private fun JsonObject?.toPatchKindLabel(): String = when (this?.string("type")) {
    "add" -> "Add"
    "delete" -> "Delete"
    "update" -> string("move_path")?.let { movePath -> "Update -> $movePath" } ?: "Update"
    else -> "Change"
}

private fun JsonArray?.toUserInputContents(): List<UserInputContent> = this
    ?.mapNotNull { item ->
        val jsonItem = item.jsonObject
        when (jsonItem.string("type")) {
            "text" -> UserInputContent.Text(
                text = jsonItem.string("text").orEmpty(),
                placeholders = jsonItem.arrayAt("text_elements")
                    ?.mapNotNull { element ->
                        element.jsonObject.string("placeholder")
                    }
                    .orEmpty(),
            )

            "image" -> jsonItem.string("url")?.let(UserInputContent::Image)
            "localImage" -> jsonItem.string("path")?.let(UserInputContent::LocalImage)
            "skill" -> {
                val name = jsonItem.string("name")
                val path = jsonItem.string("path")
                if (name != null && path != null) UserInputContent.Skill(name = name, path = path) else null
            }

            "mention" -> {
                val name = jsonItem.string("name")
                val path = jsonItem.string("path")
                if (name != null && path != null) UserInputContent.Mention(name = name, path = path) else null
            }

            else -> null
        }
    }
    .orEmpty()

private fun List<UserInputContent>.toUserInputText(): String {
    val textContent = filterIsInstance<UserInputContent.Text>()
        .joinToString(separator = "\n") { it.text }
        .trim()

    if (textContent.isNotBlank()) {
        return textContent
    }

    val imageCount = count { it is UserInputContent.Image || it is UserInputContent.LocalImage }
    return when {
        imageCount == 1 && size == 1 -> "Image attached"
        imageCount > 1 && imageCount == size -> "$imageCount images attached"
        size == 1 && firstOrNull() is UserInputContent.Skill -> "Skill attached"
        size == 1 && firstOrNull() is UserInputContent.Mention -> "App attached"
        size > 0 -> "$size attachment(s)"
        else -> "User input"
    }
}

private fun JsonObject.toToolContentItemOrNull(): ToolContentItem? = when (string("type")) {
    "inputText" -> ToolContentItem.Text(text = string("text").orEmpty())
    "inputImage" -> ToolContentItem.Image(imageUrl = string("imageUrl").orEmpty())
    else -> null
}

private fun JsonObject.toWebSearchActionLabel(): String = when (string("type")) {
    "search" -> listOfNotNull(
        string("query"),
        arrayAt("queries")
            ?.map { it.jsonPrimitive.content }
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString(),
    ).joinToString(separator = " | ").ifBlank { "Search" }

    "openPage" -> string("url") ?: "Open page"
    "findInPage" -> listOfNotNull(string("url"), string("pattern")).joinToString(separator = " | ").ifBlank { "Find in page" }
    else -> "Other action"
}

private fun JsonObject.availableCommandDecisions(): List<ApprovalDecision> {
    val values = arrayAt("availableDecisions")
        ?.mapNotNull { decision ->
            when (decision.jsonPrimitive.content) {
                "accept" -> ApprovalDecision.Accept
                "acceptForSession" -> ApprovalDecision.AcceptForSession
                "decline" -> ApprovalDecision.Decline
                "cancel" -> ApprovalDecision.Cancel
                else -> null
            }
        }
        .orEmpty()

    return values.ifEmpty {
        listOf(
            ApprovalDecision.Accept,
            ApprovalDecision.AcceptForSession,
            ApprovalDecision.Decline,
            ApprovalDecision.Cancel,
        )
    }
}

private fun JsonObject.requireThreadItemId(): String = string("id")
    ?: "unknown-${string("type").orEmpty()}"

private fun String?.toThreadItemStatus(): ThreadItemStatus = when (this) {
    "completed" -> ThreadItemStatus.Completed
    "failed" -> ThreadItemStatus.Failed
    "declined" -> ThreadItemStatus.Declined
    else -> ThreadItemStatus.InProgress
}

private fun String?.toComposerReasoningEffort(): ComposerReasoningEffort = when (this) {
    "none" -> ComposerReasoningEffort.None
    "minimal" -> ComposerReasoningEffort.Minimal
    "low" -> ComposerReasoningEffort.Low
    "high" -> ComposerReasoningEffort.High
    "xhigh" -> ComposerReasoningEffort.XHigh
    else -> ComposerReasoningEffort.Medium
}

private fun String?.toNullableComposerReasoningEffort(): ComposerReasoningEffort? = when (this) {
    "none" -> ComposerReasoningEffort.None
    "minimal" -> ComposerReasoningEffort.Minimal
    "low" -> ComposerReasoningEffort.Low
    "medium" -> ComposerReasoningEffort.Medium
    "high" -> ComposerReasoningEffort.High
    "xhigh" -> ComposerReasoningEffort.XHigh
    null -> null
    else -> null
}

private fun JsonElement?.toThreadSourceKind(): ThreadSourceKind = when (this) {
    is JsonPrimitive -> when (content) {
        "cli" -> ThreadSourceKind.Cli
        "vscode" -> ThreadSourceKind.VsCode
        "exec" -> ThreadSourceKind.Exec
        "appServer" -> ThreadSourceKind.AppServer
        "unknown" -> ThreadSourceKind.Unknown
        else -> ThreadSourceKind.Unknown
    }

    is JsonObject -> if ("subAgent" in keys) {
        ThreadSourceKind.SubAgent
    } else {
        ThreadSourceKind.Unknown
    }

    else -> ThreadSourceKind.Unknown
}
