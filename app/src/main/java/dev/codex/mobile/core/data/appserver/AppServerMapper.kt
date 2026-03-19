package dev.codex.mobile.core.data.appserver

import dev.codex.mobile.core.model.AccountState
import dev.codex.mobile.core.model.AccountStatus
import dev.codex.mobile.core.model.AccountRateLimit
import dev.codex.mobile.core.model.AccountRateLimits
import dev.codex.mobile.core.model.AccountRateLimitWindow
import dev.codex.mobile.core.model.ApprovalDecision
import dev.codex.mobile.core.model.ApprovalItem
import dev.codex.mobile.core.model.ApprovalKind
import dev.codex.mobile.core.model.ApprovalNetworkContext
import dev.codex.mobile.core.model.ComposerCatalog
import dev.codex.mobile.core.model.ComposerModelOption
import dev.codex.mobile.core.model.ComposerReasoningEffort
import dev.codex.mobile.core.model.ComposerReasoningEffortOption
import dev.codex.mobile.core.model.ComposerSkillOption
import dev.codex.mobile.core.model.CollabAgentState
import dev.codex.mobile.core.model.CommandActionHint
import dev.codex.mobile.core.model.FileChangeEntry
import dev.codex.mobile.core.model.ThreadDetail
import dev.codex.mobile.core.model.ThreadDynamicToolKind
import dev.codex.mobile.core.model.ThreadDynamicToolRequest
import dev.codex.mobile.core.model.ThreadItem
import dev.codex.mobile.core.model.ThreadItemStatus
import dev.codex.mobile.core.model.ThreadSourceKind
import dev.codex.mobile.core.model.ThreadStatus
import dev.codex.mobile.core.model.ThreadStatusType
import dev.codex.mobile.core.model.ThreadSummary
import dev.codex.mobile.core.model.ThreadUserInputAnswer
import dev.codex.mobile.core.model.ThreadUserInputField
import dev.codex.mobile.core.model.ThreadUserInputFieldKind
import dev.codex.mobile.core.model.ToolContentItem
import dev.codex.mobile.core.model.approvalAnswersFor
import dev.codex.mobile.core.model.ThreadUserInputOption
import dev.codex.mobile.core.model.ThreadUserInputPayload
import dev.codex.mobile.core.model.ThreadUserInputQuestion
import dev.codex.mobile.core.model.ThreadUserInputRequest
import dev.codex.mobile.core.model.ThreadUserInputResponse
import dev.codex.mobile.core.model.ThreadUserInputTextFormat
import dev.codex.mobile.core.model.UserInputContent
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
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

internal fun JsonObject.toAccountRateLimits(): AccountRateLimits {
    val currentRateLimit: AccountRateLimit? = objectAt("rateLimits")?.toAccountRateLimit()
    val rateLimitsByLimitId: Map<String, AccountRateLimit> = objectAt("rateLimitsByLimitId")
        ?.entries
        ?.associate { (limitId, value) ->
            val bucket: AccountRateLimit = value.jsonObject.toAccountRateLimit()
                .let { parsed ->
                    if (parsed.limitId.isBlank()) parsed.copy(limitId = limitId) else parsed
                }
            limitId to bucket
        }
        .orEmpty()

    return AccountRateLimits(
        current = currentRateLimit ?: rateLimitsByLimitId["codex"] ?: rateLimitsByLimitId.values.firstOrNull(),
        byLimitId = rateLimitsByLimitId,
    )
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

internal fun JsonObject.toAccountRateLimit(): AccountRateLimit = AccountRateLimit(
    limitId = string("limitId").orEmpty(),
    limitName = string("limitName"),
    primary = objectAt("primary")?.toAccountRateLimitWindow(),
    secondary = objectAt("secondary")?.toAccountRateLimitWindow(),
)

internal fun JsonObject.toAccountRateLimitWindow(): AccountRateLimitWindow = AccountRateLimitWindow(
    usedPercent = int("usedPercent"),
    windowDurationMins = int("windowDurationMins"),
    resetsAtEpochSeconds = long("resetsAt"),
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
            commandActions = params.arrayAt("commandActions")
                ?.mapNotNull { action -> action.jsonObject.toCommandActionHint() }
                .orEmpty(),
            networkContext = params.objectAt("networkApprovalContext")?.toApprovalNetworkContext(),
            requestedPermissions = params.objectAt("additionalPermissions")?.toApprovalPermissionsOrNull(),
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

    "item/permissions/requestApproval" -> {
        val params = requireNotNull(objectAt("params"))
        ApprovalItem(
            id = requestId.content,
            threadId = requireNotNull(params.string("threadId")),
            turnId = requireNotNull(params.string("turnId")),
            itemId = requireNotNull(params.string("itemId")),
            kind = ApprovalKind.Permissions,
            requestedPermissions = params.objectAt("permissions")?.toApprovalPermissionsOrNull(),
            reason = params.string("reason"),
            availableDecisions = listOf(
                ApprovalDecision.Accept,
                ApprovalDecision.AcceptForSession,
                ApprovalDecision.Decline,
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
            turnId = params.string("turnId"),
            itemId = params.string("itemId"),
            payload = ThreadUserInputPayload.ToolQuestions(
                questions = params.arrayAt("questions")
                    ?.mapNotNull { question -> question.jsonObject.toThreadUserInputQuestion() }
                    .orEmpty(),
            ),
        )
    }

    "mcpServer/elicitation/request" -> {
        val params = requireNotNull(objectAt("params"))
        ThreadUserInputRequest(
            requestId = requestId.content,
            threadId = requireNotNull(params.string("threadId")),
            turnId = params.string("turnId"),
            itemId = null,
            payload = params.toMcpUserInputPayload() ?: return null,
        )
    }

    else -> null
}

internal fun JsonObject.toThreadDynamicToolRequest(requestId: JsonPrimitive): ThreadDynamicToolRequest? {
    if (string("method") != "item/tool/call") return null
    val params = requireNotNull(objectAt("params"))
    val threadId = requireNotNull(params.string("threadId"))
    val tool = requireNotNull(params.string("tool"))
    val kind = when (tool) {
        "pick_photo" -> ThreadDynamicToolKind.PickPhoto
        else -> return null
    }
    val argumentsElement = params.elementAt("arguments")
    val argumentsObject = argumentsElement as? JsonObject
    return ThreadDynamicToolRequest(
        requestId = requestId.content,
        threadId = threadId,
        turnId = params.string("turnId"),
        itemId = params.string("itemId"),
        tool = tool,
        kind = kind,
        prompt = argumentsObject?.string("reason")
            ?: argumentsObject?.string("prompt")
            ?: argumentsObject?.string("message"),
        arguments = argumentsElement?.toDisplayJson().orEmpty(),
    )
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
        receiverThreadIds = toCollabReceiverThreadIds(),
        prompt = string("prompt"),
        agentStates = toCollabAgentStates(),
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

internal fun commandApprovalDecisionPayload(decision: ApprovalDecision): JsonObject = buildJsonObject {
    put("decision", commandApprovalDecisionElement(decision))
}

internal fun fileChangeApprovalDecisionPayload(decision: ApprovalDecision): JsonObject = buildJsonObject {
    put("decision", fileChangeApprovalDecisionElement(decision))
}

internal fun permissionsApprovalPayload(
    decision: ApprovalDecision,
    requestedPermissions: JsonObject?,
): JsonObject = buildJsonObject {
    when (decision) {
        ApprovalDecision.Accept -> {
            put("permissions", requestedPermissions ?: emptyJsonObject)
            put("scope", "turn")
        }

        ApprovalDecision.AcceptForSession -> {
            put("permissions", requestedPermissions ?: emptyJsonObject)
            put("scope", "session")
        }

        ApprovalDecision.Decline,
        ApprovalDecision.Cancel,
        is ApprovalDecision.AcceptWithExecpolicyAmendment,
        is ApprovalDecision.ApplyNetworkPolicyAmendment,
        -> {
            put("permissions", emptyJsonObject)
        }
    }
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

internal fun dynamicToolCallResponsePayload(
    contentItems: List<JsonObject> = emptyList(),
    success: Boolean? = null,
): JsonObject = buildJsonObject {
    if (contentItems.isNotEmpty()) {
        put(
            "contentItems",
            buildJsonArray {
                contentItems.forEach(::add)
            },
        )
    }
    success?.let { put("success", it) }
}

internal fun dynamicToolImageContentItemPayload(
    imageUrl: String,
): JsonObject = buildJsonObject {
    put("type", "inputImage")
    put("imageUrl", imageUrl)
}

internal fun userInputResponsePayload(
    request: ThreadUserInputRequest,
    response: ThreadUserInputResponse,
): JsonObject? = when (val payload = request.payload) {
    is ThreadUserInputPayload.ToolQuestions -> {
        request.approvalAnswersFor(response)
            ?.let(::toolRequestUserInputResponsePayload)
            ?.let { return it }
        val acceptResponse = response as? ThreadUserInputResponse.Accept ?: return null
        val answers = acceptResponse.answers.mapValues { (_, answer) ->
            when (answer) {
                is ThreadUserInputAnswer.TextList -> answer.values
                is ThreadUserInputAnswer.BooleanValue -> listOf(answer.value.toString())
            }
        }
        if (answers.isEmpty()) {
            null
        } else {
            toolRequestUserInputResponsePayload(answers = answers)
        }
    }

    is ThreadUserInputPayload.McpForm -> buildJsonObject {
        when (response) {
            is ThreadUserInputResponse.Accept -> {
                put("action", "accept")
                put(
                    "content",
                    payload.toMcpElicitationContent(response.answers),
                )
            }

            ThreadUserInputResponse.Decline -> put("action", "decline")
            ThreadUserInputResponse.Cancel -> put("action", "cancel")
        }
    }

    is ThreadUserInputPayload.McpUrl -> buildJsonObject {
        put(
            "action",
            when (response) {
                is ThreadUserInputResponse.Accept -> "accept"
                ThreadUserInputResponse.Decline -> "decline"
                ThreadUserInputResponse.Cancel -> "cancel"
            },
        )
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
    val description = string("description").orEmpty()
    return ThreadUserInputOption(
        value = label,
        label = label,
        description = description,
    )
}

private fun JsonObject.toMcpUserInputPayload(): ThreadUserInputPayload? {
    val serverName = string("serverName") ?: return null
    val message = string("message").orEmpty()
    return when (string("mode")) {
        "form" -> {
            val requestedSchema = objectAt("requestedSchema") ?: return null
            val requiredKeys: Set<String> = requestedSchema.arrayAt("required")
                ?.map { item -> item.jsonPrimitive.content }
                ?.toSet()
                .orEmpty()
            val fields: List<ThreadUserInputField> = requestedSchema.objectAt("properties")
                ?.entries
                ?.mapNotNull { (key, value) ->
                    value.jsonObject.toThreadUserInputField(
                        key = key,
                        required = key in requiredKeys,
                    )
                }
                .orEmpty()
            ThreadUserInputPayload.McpForm(
                serverName = serverName,
                message = message,
                fields = fields,
            )
        }

        "url" -> ThreadUserInputPayload.McpUrl(
            serverName = serverName,
            message = message,
            url = requireNotNull(string("url")),
            elicitationId = requireNotNull(string("elicitationId")),
        )

        else -> null
    }
}

private fun JsonObject.toThreadUserInputField(
    key: String,
    required: Boolean,
): ThreadUserInputField? {
    val label = string("title")?.takeIf(String::isNotBlank) ?: key
    val description = string("description")?.takeIf(String::isNotBlank)
    val fieldKind = when (string("type")) {
        "string" -> toStringFieldKind()
        "number",
        "integer",
        -> toNumberFieldKind()

        "boolean" -> ThreadUserInputFieldKind.Toggle(
            defaultValue = boolean("default"),
        )

        "array" -> toMultiSelectFieldKind()
        else -> null
    } ?: return null

    return ThreadUserInputField(
        key = key,
        label = label,
        description = description,
        required = required,
        kind = fieldKind,
    )
}

private fun JsonObject.toStringFieldKind(): ThreadUserInputFieldKind? {
    val oneOfOptions = arrayAt("oneOf")
        ?.mapNotNull { option -> option.jsonObject.toConstThreadUserInputOption() }
        .orEmpty()
    if (oneOfOptions.isNotEmpty()) {
        return ThreadUserInputFieldKind.SingleSelect(
            options = oneOfOptions,
            defaultValue = string("default"),
        )
    }

    val enumOptions = arrayAt("enum")
        ?.map { option -> option.jsonPrimitive.content }
        .orEmpty()
    if (enumOptions.isNotEmpty()) {
        val enumNames = arrayAt("enumNames")
            ?.map { option -> option.jsonPrimitive.content }
            .orEmpty()
        return ThreadUserInputFieldKind.SingleSelect(
            options = enumOptions.mapIndexed { index, value ->
                ThreadUserInputOption(
                    value = value,
                    label = enumNames.getOrNull(index) ?: value,
                )
            },
            defaultValue = string("default"),
        )
    }

    return ThreadUserInputFieldKind.Text(
        defaultValue = string("default"),
        format = string("format").toThreadUserInputTextFormat(),
        minLength = int("minLength"),
        maxLength = int("maxLength"),
    )
}

private fun JsonObject.toNumberFieldKind(): ThreadUserInputFieldKind.Number = ThreadUserInputFieldKind.Number(
    defaultValue = primitiveDouble("default"),
    isInteger = string("type") == "integer",
    minimum = primitiveDouble("minimum"),
    maximum = primitiveDouble("maximum"),
)

private fun JsonObject.toMultiSelectFieldKind(): ThreadUserInputFieldKind.MultiSelect? {
    val items = objectAt("items") ?: return null
    val titledOptions = items.arrayAt("anyOf")
        ?.mapNotNull { option -> option.jsonObject.toConstThreadUserInputOption() }
        .orEmpty()
    val options = if (titledOptions.isNotEmpty()) {
        titledOptions
    } else {
        items.arrayAt("enum")
            ?.map { option ->
                ThreadUserInputOption(
                    value = option.jsonPrimitive.content,
                    label = option.jsonPrimitive.content,
                )
            }
            .orEmpty()
    }
    if (options.isEmpty()) return null

    return ThreadUserInputFieldKind.MultiSelect(
        options = options,
        defaultValues = arrayAt("default")
            ?.map { value -> value.jsonPrimitive.content }
            .orEmpty(),
        minItems = int("minItems"),
        maxItems = int("maxItems"),
    )
}

private fun JsonObject.toConstThreadUserInputOption(): ThreadUserInputOption? {
    val value = string("const") ?: return null
    val label = string("title") ?: return null
    return ThreadUserInputOption(
        value = value,
        label = label,
    )
}

private fun ThreadUserInputPayload.McpForm.toMcpElicitationContent(
    answers: Map<String, ThreadUserInputAnswer>,
): JsonObject = buildJsonObject {
    fields.forEach { field ->
        val serializedAnswer = field.toJsonElementOrNull(answers[field.key])
        if (serializedAnswer != null) {
            put(field.key, serializedAnswer)
        }
    }
}

private fun ThreadUserInputField.toJsonElementOrNull(
    answer: ThreadUserInputAnswer?,
): JsonElement? = when (val currentKind = kind) {
    is ThreadUserInputFieldKind.Text -> {
        val value = (answer as? ThreadUserInputAnswer.TextList)
            ?.values
            ?.firstOrNull()
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: currentKind.defaultValue
                ?.trim()
                ?.takeIf(String::isNotBlank)
        value?.let(::JsonPrimitive)
    }

    is ThreadUserInputFieldKind.Number -> {
        val rawValue = (answer as? ThreadUserInputAnswer.TextList)
            ?.values
            ?.firstOrNull()
            ?.trim()
            ?.takeIf(String::isNotBlank)
        val numericValue = rawValue?.toDoubleOrNull() ?: currentKind.defaultValue
        when {
            numericValue == null -> null
            currentKind.isInteger -> JsonPrimitive(numericValue.toLong())
            else -> JsonPrimitive(numericValue)
        }
    }

    is ThreadUserInputFieldKind.Toggle -> {
        val value = (answer as? ThreadUserInputAnswer.BooleanValue)?.value ?: currentKind.defaultValue ?: return null
        JsonPrimitive(value)
    }

    is ThreadUserInputFieldKind.SingleSelect -> {
        val value = (answer as? ThreadUserInputAnswer.TextList)
            ?.values
            ?.firstOrNull()
            ?.takeIf(String::isNotBlank)
            ?: currentKind.defaultValue
                ?.takeIf(String::isNotBlank)
        value?.let(::JsonPrimitive)
    }

    is ThreadUserInputFieldKind.MultiSelect -> {
        val values = (answer as? ThreadUserInputAnswer.TextList)?.values
            ?: if (required) currentKind.defaultValues else currentKind.defaultValues.takeIf(List<String>::isNotEmpty)
            ?: return null
        buildJsonArray {
            values.forEach { value ->
                add(JsonPrimitive(value))
            }
        }
    }
}

private fun JsonObject.primitiveDouble(name: String): Double? = this[name]
    ?.jsonPrimitive
    ?.content
    ?.toDoubleOrNull()

private fun String?.toThreadUserInputTextFormat(): ThreadUserInputTextFormat = when (this) {
    "email" -> ThreadUserInputTextFormat.Email
    "uri" -> ThreadUserInputTextFormat.Uri
    "date" -> ThreadUserInputTextFormat.Date
    "date-time" -> ThreadUserInputTextFormat.DateTime
    else -> ThreadUserInputTextFormat.PlainText
}

private fun JsonObject.toApprovalNetworkContext(): ApprovalNetworkContext? {
    val host = string("host") ?: return null
    val protocol = string("protocol") ?: return null
    return ApprovalNetworkContext(
        host = host,
        protocol = protocol,
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
        ?.mapNotNull(JsonElement::toApprovalDecisionOrNull)
        .orEmpty()

    if (values.isNotEmpty()) {
        return values
    }

    val proposedExecpolicyAmendment: List<String> = arrayAt("proposedExecpolicyAmendment")
        ?.map { item -> item.jsonPrimitive.content }
        .orEmpty()
    val proposedNetworkPolicyAmendments: List<ApprovalDecision.ApplyNetworkPolicyAmendment> =
        arrayAt("proposedNetworkPolicyAmendments")
            ?.mapNotNull { item ->
                item.jsonObject.toNetworkPolicyDecisionOrNull()
            }
            .orEmpty()

    return buildList {
        add(ApprovalDecision.Accept)
        if (proposedExecpolicyAmendment.isNotEmpty()) {
            add(
                ApprovalDecision.AcceptWithExecpolicyAmendment(
                    execpolicyAmendment = proposedExecpolicyAmendment,
                ),
            )
        } else if (proposedNetworkPolicyAmendments.isEmpty()) {
            add(ApprovalDecision.AcceptForSession)
        }
        addAll(proposedNetworkPolicyAmendments)
        add(ApprovalDecision.Decline)
        add(ApprovalDecision.Cancel)
    }
}

private fun JsonElement.toApprovalDecisionOrNull(): ApprovalDecision? = when (this) {
    is JsonPrimitive -> when (content) {
        "accept" -> ApprovalDecision.Accept
        "acceptForSession" -> ApprovalDecision.AcceptForSession
        "decline" -> ApprovalDecision.Decline
        "cancel" -> ApprovalDecision.Cancel
        else -> null
    }

    is JsonObject -> when {
        objectAt("acceptWithExecpolicyAmendment") != null -> {
            val amendment = objectAt("acceptWithExecpolicyAmendment")
                ?.arrayAt("execpolicy_amendment")
                ?.map { item -> item.jsonPrimitive.content }
                .orEmpty()
            ApprovalDecision.AcceptWithExecpolicyAmendment(execpolicyAmendment = amendment)
        }

        objectAt("applyNetworkPolicyAmendment") != null -> {
            val amendment = objectAt("applyNetworkPolicyAmendment")
                ?.objectAt("network_policy_amendment")
                ?.toNetworkPolicyDecisionOrNull()
                ?: return null
            amendment
        }

        else -> null
    }

    else -> null
}

private fun JsonObject.toApprovalPermissionsOrNull(): dev.codex.mobile.core.model.ApprovalPermissions? {
    val fileSystem = objectAt("fileSystem")
    val macOs = objectAt("macos")
    val network = objectAt("network")

    val summary = dev.codex.mobile.core.model.ApprovalPermissions(
        readPaths = fileSystem?.arrayAt("read")
            ?.map { item -> item.jsonPrimitive.content }
            .orEmpty(),
        writePaths = fileSystem?.arrayAt("write")
            ?.map { item -> item.jsonPrimitive.content }
            .orEmpty(),
        networkEnabled = network?.boolean("enabled") == true,
        macOsAccessibility = macOs?.boolean("accessibility") == true,
        macOsAutomationAll = macOs?.string("automations") == "all",
        macOsAutomationBundleIds = macOs?.objectAt("automations")
            ?.arrayAt("bundle_ids")
            ?.map { item -> item.jsonPrimitive.content }
            .orEmpty(),
        macOsCalendar = macOs?.boolean("calendar") == true,
        macOsPreferences = macOs?.string("preferences"),
    )

    return summary.takeUnless(dev.codex.mobile.core.model.ApprovalPermissions::isEmpty)
}

private fun JsonObject.toNetworkPolicyDecisionOrNull(): ApprovalDecision.ApplyNetworkPolicyAmendment? {
    val action = string("action") ?: return null
    val host = string("host") ?: return null
    return ApprovalDecision.ApplyNetworkPolicyAmendment(
        action = action,
        host = host,
    )
}

private fun commandApprovalDecisionElement(decision: ApprovalDecision): JsonElement = when (decision) {
    ApprovalDecision.Accept -> JsonPrimitive("accept")
    ApprovalDecision.AcceptForSession -> JsonPrimitive("acceptForSession")
    is ApprovalDecision.AcceptWithExecpolicyAmendment -> buildJsonObject {
        putJsonObject("acceptWithExecpolicyAmendment") {
            put(
                "execpolicy_amendment",
                buildJsonArray {
                    decision.execpolicyAmendment.forEach { amendment ->
                        add(JsonPrimitive(amendment))
                    }
                },
            )
        }
    }

    is ApprovalDecision.ApplyNetworkPolicyAmendment -> buildJsonObject {
        putJsonObject("applyNetworkPolicyAmendment") {
            putJsonObject("network_policy_amendment") {
                put("action", decision.action)
                put("host", decision.host)
            }
        }
    }

    ApprovalDecision.Decline -> JsonPrimitive("decline")
    ApprovalDecision.Cancel -> JsonPrimitive("cancel")
}

private fun fileChangeApprovalDecisionElement(decision: ApprovalDecision): JsonPrimitive = when (decision) {
    ApprovalDecision.Accept -> JsonPrimitive("accept")
    ApprovalDecision.AcceptForSession -> JsonPrimitive("acceptForSession")
    is ApprovalDecision.AcceptWithExecpolicyAmendment -> JsonPrimitive("accept")
    is ApprovalDecision.ApplyNetworkPolicyAmendment -> JsonPrimitive("accept")
    ApprovalDecision.Decline -> JsonPrimitive("decline")
    ApprovalDecision.Cancel -> JsonPrimitive("cancel")
}

private fun JsonObject.requireThreadItemId(): String = string("id")
    ?: "unknown-${string("type").orEmpty()}"

private fun JsonObject.toCollabReceiverThreadIds(): List<String> = buildList {
    arrayAt("receiverThreadIds")
        ?.map { receiver -> receiver.jsonPrimitive.content }
        ?.forEach(::add)
    string("receiverThreadId")
        ?.takeIf(String::isNotBlank)
        ?.let(::add)
    string("newThreadId")
        ?.takeIf(String::isNotBlank)
        ?.let(::add)
}.distinct()

private fun JsonObject.toCollabAgentStates(): List<CollabAgentState> {
    objectAt("agentsStates")?.let { statesByThreadId ->
        return statesByThreadId.toCollabAgentStatesByThreadId()
    }

    val receiverThreadIds = toCollabReceiverThreadIds()
    return when (val agentStatus = elementAt("agentStatus")) {
        is JsonObject -> {
            if ("status" in agentStatus || "message" in agentStatus) {
                agentStatus.toCollabAgentState(receiverThreadIds.firstOrNull()).let(::listOfNotNull)
            } else {
                agentStatus.toCollabAgentStatesByThreadId()
            }
        }

        is JsonPrimitive -> {
            val status = agentStatus.contentOrNull.orEmpty()
            val receiverThreadId = receiverThreadIds.firstOrNull()
            if (status.isBlank() && receiverThreadId.isNullOrBlank()) {
                emptyList()
            } else {
                listOf(
                    CollabAgentState(
                        threadId = receiverThreadId.orEmpty(),
                        status = status,
                    ),
                )
            }
        }

        else -> emptyList()
    }
}

private fun JsonObject.toCollabAgentStatesByThreadId(): List<CollabAgentState> = entries.mapNotNull { (threadId, value) ->
    value.toCollabAgentState(threadId)
}

private fun JsonElement.toCollabAgentState(threadId: String?): CollabAgentState? = when (this) {
    is JsonObject -> {
        val status = string("status").orEmpty()
        val message = string("message")
        if (threadId.isNullOrBlank() && status.isBlank() && message.isNullOrBlank()) {
            null
        } else {
            CollabAgentState(
                threadId = threadId.orEmpty(),
                status = status,
                message = message,
            )
        }
    }

    is JsonPrimitive -> {
        val status = contentOrNull.orEmpty()
        if (threadId.isNullOrBlank() && status.isBlank()) {
            null
        } else {
            CollabAgentState(
                threadId = threadId.orEmpty(),
                status = status,
            )
        }
    }

    else -> null
}

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
