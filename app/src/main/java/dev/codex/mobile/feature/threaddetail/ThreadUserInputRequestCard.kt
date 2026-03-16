package dev.codex.mobile.feature.threaddetail

import android.content.Intent
import android.net.Uri
import android.util.Patterns
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.codex.mobile.core.designsystem.component.CodexCard
import dev.codex.mobile.core.designsystem.component.StatusChip
import dev.codex.mobile.core.designsystem.theme.CodexSpacing
import dev.codex.mobile.core.model.ThreadUserInputAnswer
import dev.codex.mobile.core.model.ThreadUserInputField
import dev.codex.mobile.core.model.ThreadUserInputFieldKind
import dev.codex.mobile.core.model.ThreadUserInputOption
import dev.codex.mobile.core.model.ThreadUserInputPayload
import dev.codex.mobile.core.model.ThreadUserInputQuestion
import dev.codex.mobile.core.model.ThreadUserInputRequest
import dev.codex.mobile.core.model.ThreadUserInputResponse
import dev.codex.mobile.core.model.ThreadUserInputTextFormat
import dev.codex.mobile.core.model.approvalPrompt
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZonedDateTime

private val UserInputAccent: Color = Color(0xFF3A7BD5)

@Composable
internal fun InlineUserInputRequestCard(
    request: ThreadUserInputRequest,
    onSubmit: (String, ThreadUserInputResponse) -> Unit,
    modifier: Modifier = Modifier,
) {
    CodexCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(CodexSpacing.cardPadding),
    ) {
        UserInputCardHeader(request = request)

        Spacer(modifier = Modifier.height(CodexSpacing.sectionGap))
        when (val payload = request.payload) {
            is ThreadUserInputPayload.ToolQuestions -> {
                if (request.approvalPrompt != null) {
                    ToolApprovalRequestContent(
                        request = request,
                        onSubmit = onSubmit,
                    )
                } else {
                    ToolQuestionRequestContent(
                        requestId = request.requestId,
                        questions = payload.questions,
                        onSubmit = onSubmit,
                    )
                }
            }

            is ThreadUserInputPayload.McpForm -> McpFormRequestContent(
                requestId = request.requestId,
                payload = payload,
                onSubmit = onSubmit,
            )

            is ThreadUserInputPayload.McpUrl -> McpUrlRequestContent(
                requestId = request.requestId,
                payload = payload,
                onSubmit = onSubmit,
            )
        }
    }
}

@Composable
private fun UserInputCardHeader(
    request: ThreadUserInputRequest,
) {
    val title: String
    val subtitle: String
    val chipLabel: String
    val approvalPrompt = request.approvalPrompt
    when (val payload = request.payload) {
        is ThreadUserInputPayload.ToolQuestions -> {
            if (approvalPrompt != null) {
                title = "Tool approval required"
                subtitle = approvalPrompt.prompt
                chipLabel = "Approval"
            } else {
                title = "Clarification needed"
                subtitle = "Codex is waiting for a short answer before it can continue this turn."
                chipLabel = "Needs Input"
            }
        }

        is ThreadUserInputPayload.McpForm -> {
            title = "MCP form requested"
            subtitle = payload.serverName
            chipLabel = "MCP Form"
        }

        is ThreadUserInputPayload.McpUrl -> {
            title = "MCP action required"
            subtitle = payload.serverName
            chipLabel = "Open Link"
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
                contentDescription = null,
                tint = UserInputAccent,
            )
            Spacer(modifier = Modifier.width(CodexSpacing.listGap))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.width(CodexSpacing.listGap))
        StatusChip(
            label = chipLabel,
            color = UserInputAccent,
        )
    }
}

@Composable
private fun ToolApprovalRequestContent(
    request: ThreadUserInputRequest,
    onSubmit: (String, ThreadUserInputResponse) -> Unit,
) {
    val approvalPrompt = requireNotNull(request.approvalPrompt)
    approvalPrompt.actions.forEachIndexed { index, action ->
        when (action.response) {
            is ThreadUserInputResponse.Accept -> Button(
                onClick = { onSubmit(request.requestId, action.response) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = action.label)
            }

            ThreadUserInputResponse.Decline -> OutlinedButton(
                onClick = { onSubmit(request.requestId, action.response) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = action.label)
            }

            ThreadUserInputResponse.Cancel -> OutlinedButton(
                onClick = { onSubmit(request.requestId, action.response) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = action.label)
            }
        }

        if (index != approvalPrompt.actions.lastIndex) {
            Spacer(modifier = Modifier.height(CodexSpacing.tightGap))
        }
    }
}

@Composable
private fun ToolQuestionRequestContent(
    requestId: String,
    questions: List<ThreadUserInputQuestion>,
    onSubmit: (String, ThreadUserInputResponse) -> Unit,
) {
    val selectedOptions = remember(requestId) { mutableStateMapOf<String, String>() }
    val freeTextAnswers = remember(requestId) { mutableStateMapOf<String, String>() }
    val answers = toolQuestionAnswersForSubmission(
        questions = questions,
        selectedOptions = selectedOptions,
        freeTextAnswers = freeTextAnswers,
    )
    val canSubmit = answers.size == questions.size && answers.isNotEmpty()

    questions.forEachIndexed { index, question ->
        if (index > 0) {
            Spacer(modifier = Modifier.height(CodexSpacing.listGap))
        }
        UserInputQuestionSection(
            question = question,
            selectedOption = selectedOptions[question.id],
            freeTextAnswer = freeTextAnswers[question.id].orEmpty(),
            onSelectOption = { option ->
                selectedOptions[question.id] = option.value
                freeTextAnswers.remove(question.id)
            },
            onFreeTextChanged = { value ->
                freeTextAnswers[question.id] = value
                if (value.isNotBlank()) {
                    selectedOptions.remove(question.id)
                }
            },
        )
    }

    Spacer(modifier = Modifier.height(CodexSpacing.sectionGap))
    Button(
        onClick = {
            onSubmit(
                requestId,
                ThreadUserInputResponse.Accept(answers = answers),
            )
        },
        enabled = canSubmit,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = if (questions.size == 1) {
                "Send Answer"
            } else {
                "Send Answers"
            },
        )
    }
}

@Composable
private fun McpFormRequestContent(
    requestId: String,
    payload: ThreadUserInputPayload.McpForm,
    onSubmit: (String, ThreadUserInputResponse) -> Unit,
) {
    val textAnswers = remember(requestId) {
        mutableStateMapOf<String, String>().apply {
            payload.fields.forEach { field ->
                when (val kind = field.kind) {
                    is ThreadUserInputFieldKind.Text -> kind.defaultValue?.let { put(field.key, it) }
                    is ThreadUserInputFieldKind.Number -> kind.defaultValue?.let { put(field.key, formatNumberDefault(it, kind.isInteger)) }
                    is ThreadUserInputFieldKind.SingleSelect -> kind.defaultValue?.let { put(field.key, it) }
                    is ThreadUserInputFieldKind.MultiSelect,
                    is ThreadUserInputFieldKind.Toggle,
                    -> Unit
                }
            }
        }
    }
    val toggleAnswers = remember(requestId) {
        mutableStateMapOf<String, Boolean>().apply {
            payload.fields.forEach { field ->
                val kind = field.kind as? ThreadUserInputFieldKind.Toggle ?: return@forEach
                put(field.key, kind.defaultValue ?: false)
            }
        }
    }
    val multiSelectAnswers = remember(requestId) {
        mutableStateMapOf<String, Set<String>>().apply {
            payload.fields.forEach { field ->
                val kind = field.kind as? ThreadUserInputFieldKind.MultiSelect ?: return@forEach
                if (kind.defaultValues.isNotEmpty() || field.required) {
                    put(field.key, kind.defaultValues.toSet())
                }
            }
        }
    }
    val validationErrors: Map<String, String> = payload.fields.associate { field ->
        field.key to formFieldError(
            field = field,
            textAnswers = textAnswers,
            multiSelectAnswers = multiSelectAnswers,
        ).orEmpty()
    }
    val canSubmit = validationErrors.values.none { error -> error.isNotBlank() }
    val answers = mcpFormAnswersForSubmission(
        fields = payload.fields,
        textAnswers = textAnswers,
        toggleAnswers = toggleAnswers,
        multiSelectAnswers = multiSelectAnswers,
    )

    if (payload.message.isNotBlank()) {
        Text(
            text = payload.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(CodexSpacing.sectionGap))
    }

    payload.fields.forEachIndexed { index, field ->
        if (index > 0) {
            Spacer(modifier = Modifier.height(CodexSpacing.listGap))
        }
        McpFormFieldSection(
            field = field,
            textValue = textAnswers[field.key].orEmpty(),
            toggleValue = toggleAnswers[field.key] ?: false,
            multiSelectValues = multiSelectAnswers[field.key].orEmpty(),
            errorText = validationErrors[field.key].orEmpty().takeIf(String::isNotBlank),
            onTextChanged = { value -> textAnswers[field.key] = value },
            onToggleChanged = { value -> toggleAnswers[field.key] = value },
            onSingleSelectChanged = { optionValue -> textAnswers[field.key] = optionValue },
            onMultiSelectChanged = { values -> multiSelectAnswers[field.key] = values },
        )
    }

    Spacer(modifier = Modifier.height(CodexSpacing.sectionGap))
    Button(
        onClick = {
            onSubmit(
                requestId,
                ThreadUserInputResponse.Accept(answers = answers),
            )
        },
        enabled = canSubmit,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = "Continue")
    }

    Spacer(modifier = Modifier.height(CodexSpacing.tightGap))
    OutlinedButton(
        onClick = { onSubmit(requestId, ThreadUserInputResponse.Decline) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = "Decline")
    }

    Spacer(modifier = Modifier.height(CodexSpacing.tightGap))
    OutlinedButton(
        onClick = { onSubmit(requestId, ThreadUserInputResponse.Cancel) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = "Cancel")
    }
}

@Composable
private fun McpUrlRequestContent(
    requestId: String,
    payload: ThreadUserInputPayload.McpUrl,
    onSubmit: (String, ThreadUserInputResponse) -> Unit,
) {
    val context = LocalContext.current

    if (payload.message.isNotBlank()) {
        Text(
            text = payload.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(CodexSpacing.sectionGap))
    }

    Text(
        text = "URL",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = payload.url,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )

    Spacer(modifier = Modifier.height(CodexSpacing.sectionGap))
    Button(
        onClick = {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(payload.url)),
            )
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = "Open Link")
    }

    Spacer(modifier = Modifier.height(CodexSpacing.tightGap))
    OutlinedButton(
        onClick = { onSubmit(requestId, ThreadUserInputResponse.Accept()) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = "Continue After Completing It")
    }

    Spacer(modifier = Modifier.height(CodexSpacing.tightGap))
    OutlinedButton(
        onClick = { onSubmit(requestId, ThreadUserInputResponse.Decline) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = "Decline")
    }

    Spacer(modifier = Modifier.height(CodexSpacing.tightGap))
    OutlinedButton(
        onClick = { onSubmit(requestId, ThreadUserInputResponse.Cancel) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = "Cancel")
    }
}

@Composable
private fun UserInputQuestionSection(
    question: ThreadUserInputQuestion,
    selectedOption: String?,
    freeTextAnswer: String,
    onSelectOption: (ThreadUserInputOption) -> Unit,
    onFreeTextChanged: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CodexSpacing.compactGap),
    ) {
        Text(
            text = question.header.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = question.prompt,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        question.options.forEach { option ->
            SelectableInputOption(
                option = option,
                selected = selectedOption == option.value,
                onClick = { onSelectOption(option) },
            )
        }

        if (question.options.isEmpty() || question.isOtherAllowed) {
            if (question.options.isNotEmpty()) {
                Text(
                    text = "Or type another answer",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = freeTextAnswer,
                onValueChange = onFreeTextChanged,
                modifier = Modifier.fillMaxWidth(),
                singleLine = !question.isSecret,
                label = {
                    Text(
                        text = if (question.options.isEmpty()) {
                            "Your answer"
                        } else {
                            "Other answer"
                        },
                    )
                },
                shape = MaterialTheme.shapes.medium,
                visualTransformation = if (question.isSecret) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
            )
        }
    }
}

@Composable
private fun McpFormFieldSection(
    field: ThreadUserInputField,
    textValue: String,
    toggleValue: Boolean,
    multiSelectValues: Set<String>,
    errorText: String?,
    onTextChanged: (String) -> Unit,
    onToggleChanged: (Boolean) -> Unit,
    onSingleSelectChanged: (String) -> Unit,
    onMultiSelectChanged: (Set<String>) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CodexSpacing.compactGap),
    ) {
        Text(
            text = fieldLabel(field),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        field.description?.takeIf(String::isNotBlank)?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when (val kind = field.kind) {
            is ThreadUserInputFieldKind.Text -> OutlinedTextField(
                value = textValue,
                onValueChange = onTextChanged,
                modifier = Modifier.fillMaxWidth(),
                singleLine = kind.format != ThreadUserInputTextFormat.PlainText,
                label = { Text(text = field.label) },
                supportingText = {
                    fieldSupportingText(
                        field = field,
                        errorText = errorText,
                    )
                },
            )

            is ThreadUserInputFieldKind.Number -> OutlinedTextField(
                value = textValue,
                onValueChange = onTextChanged,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(text = field.label) },
                supportingText = {
                    fieldSupportingText(
                        field = field,
                        errorText = errorText,
                    )
                },
            )

            is ThreadUserInputFieldKind.Toggle -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (toggleValue) "Enabled" else "Disabled",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            errorText?.let { error ->
                                Text(
                                    text = error,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(CodexSpacing.listGap))
                        Switch(
                            checked = toggleValue,
                            onCheckedChange = onToggleChanged,
                        )
                    }
                }
            }

            is ThreadUserInputFieldKind.SingleSelect -> {
                kind.options.forEach { option ->
                    SelectableInputOption(
                        option = option,
                        selected = textValue == option.value,
                        onClick = { onSingleSelectChanged(option.value) },
                    )
                }
                errorText?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            is ThreadUserInputFieldKind.MultiSelect -> {
                kind.options.forEach { option ->
                    SelectableInputOption(
                        option = option,
                        selected = option.value in multiSelectValues,
                        onClick = {
                            val updatedValues = if (option.value in multiSelectValues) {
                                multiSelectValues - option.value
                            } else {
                                multiSelectValues + option.value
                            }
                            onMultiSelectChanged(updatedValues)
                        },
                    )
                }
                errorText?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectableInputOption(
    option: ThreadUserInputOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = if (selected) {
            UserInputAccent.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                UserInputAccent.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(CodexSpacing.microGap),
        ) {
            Text(
                text = option.label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) UserInputAccent else MaterialTheme.colorScheme.onSurface,
            )
            if (option.description.isNotBlank()) {
                Text(
                    text = option.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun fieldSupportingText(
    field: ThreadUserInputField,
    errorText: String?,
) {
    val supportingText = errorText ?: fieldConstraintSummary(field)
    if (!supportingText.isNullOrBlank()) {
        Text(
            text = supportingText,
            color = if (errorText == null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
        )
    }
}

private fun toolQuestionAnswersForSubmission(
    questions: List<ThreadUserInputQuestion>,
    selectedOptions: Map<String, String>,
    freeTextAnswers: Map<String, String>,
): Map<String, ThreadUserInputAnswer> = buildMap {
    questions.forEach { question ->
        val value = when {
            !selectedOptions[question.id].isNullOrBlank() -> selectedOptions.getValue(question.id)
            question.options.isEmpty() || question.isOtherAllowed -> freeTextAnswers[question.id]
                ?.trim()
                ?.takeIf(String::isNotBlank)

            else -> null
        }
        if (value != null) {
            put(
                question.id,
                ThreadUserInputAnswer.TextList(values = listOf(value)),
            )
        }
    }
}

private fun mcpFormAnswersForSubmission(
    fields: List<ThreadUserInputField>,
    textAnswers: Map<String, String>,
    toggleAnswers: Map<String, Boolean>,
    multiSelectAnswers: Map<String, Set<String>>,
): Map<String, ThreadUserInputAnswer> = buildMap {
    fields.forEach { field ->
        when (field.kind) {
            is ThreadUserInputFieldKind.Text,
            is ThreadUserInputFieldKind.Number,
            is ThreadUserInputFieldKind.SingleSelect,
            -> {
                val value = textAnswers[field.key]
                if (value != null) {
                    put(
                        field.key,
                        ThreadUserInputAnswer.TextList(values = listOf(value)),
                    )
                }
            }

            is ThreadUserInputFieldKind.Toggle -> {
                put(
                    field.key,
                    ThreadUserInputAnswer.BooleanValue(
                        value = toggleAnswers[field.key] ?: false,
                    ),
                )
            }

            is ThreadUserInputFieldKind.MultiSelect -> {
                val values = multiSelectAnswers[field.key]
                if (values != null) {
                    put(
                        field.key,
                        ThreadUserInputAnswer.TextList(
                            values = values.toList(),
                        ),
                    )
                }
            }
        }
    }
}

private fun formFieldError(
    field: ThreadUserInputField,
    textAnswers: Map<String, String>,
    multiSelectAnswers: Map<String, Set<String>>,
): String? = when (val kind = field.kind) {
    is ThreadUserInputFieldKind.Text -> {
        val value = textAnswers[field.key].orEmpty().trim()
        when {
            field.required && value.isBlank() -> "Required"
            value.isNotBlank() && kind.minLength != null && value.length < kind.minLength -> "Use at least ${kind.minLength} characters"
            value.isNotBlank() && kind.maxLength != null && value.length > kind.maxLength -> "Keep this under ${kind.maxLength} characters"
            value.isNotBlank() && !value.isValidFor(kind.format) -> invalidTextFormatMessage(kind.format)
            else -> null
        }
    }

    is ThreadUserInputFieldKind.Number -> {
        val rawValue = textAnswers[field.key].orEmpty().trim()
        if (rawValue.isBlank()) {
            if (field.required) "Required" else null
        } else {
            when {
                kind.isInteger && rawValue.toLongOrNull() == null -> "Enter a valid integer"
                !kind.isInteger && rawValue.toDoubleOrNull() == null -> "Enter a valid number"
                else -> {
                    val parsed = rawValue.toDoubleOrNull()
                    when {
                        parsed == null -> "Enter a valid number"
                        kind.minimum != null && parsed < kind.minimum -> "Use a value of at least ${kind.minimum}"
                        kind.maximum != null && parsed > kind.maximum -> "Use a value of at most ${kind.maximum}"
                        else -> null
                    }
                }
            }
        }
    }

    is ThreadUserInputFieldKind.Toggle -> null

    is ThreadUserInputFieldKind.SingleSelect -> if (
        field.required && textAnswers[field.key].isNullOrBlank()
    ) {
        "Choose one option"
    } else {
        null
    }

    is ThreadUserInputFieldKind.MultiSelect -> {
        val selectionCount = multiSelectAnswers[field.key]?.size ?: 0
        when {
            kind.minItems != null && selectionCount < kind.minItems -> "Choose at least ${kind.minItems}"
            kind.maxItems != null && selectionCount > kind.maxItems -> "Choose no more than ${kind.maxItems}"
            else -> null
        }
    }
}

private fun fieldConstraintSummary(field: ThreadUserInputField): String? = when (val kind = field.kind) {
    is ThreadUserInputFieldKind.Text -> buildList {
        if (kind.format != ThreadUserInputTextFormat.PlainText) {
            add(
                when (kind.format) {
                    ThreadUserInputTextFormat.Email -> "Email address"
                    ThreadUserInputTextFormat.Uri -> "URL"
                    ThreadUserInputTextFormat.Date -> "Format: YYYY-MM-DD"
                    ThreadUserInputTextFormat.DateTime -> "Format: ISO date-time"
                    ThreadUserInputTextFormat.PlainText -> ""
                },
            )
        }
        if (kind.minLength != null) add("Min ${kind.minLength} chars")
        if (kind.maxLength != null) add("Max ${kind.maxLength} chars")
        if (field.required) add("Required")
    }.filter(String::isNotBlank).joinToString(separator = " • ").ifBlank { null }

    is ThreadUserInputFieldKind.Number -> buildList {
        if (kind.isInteger) add("Integer only")
        kind.minimum?.let { add("Min $it") }
        kind.maximum?.let { add("Max $it") }
        if (field.required) add("Required")
    }.joinToString(separator = " • ").ifBlank { null }

    is ThreadUserInputFieldKind.Toggle -> if (field.required) "Required" else null

    is ThreadUserInputFieldKind.SingleSelect -> if (field.required) "Required" else null

    is ThreadUserInputFieldKind.MultiSelect -> buildList {
        kind.minItems?.let { add("Choose at least $it") }
        kind.maxItems?.let { add("Choose up to $it") }
        if (field.required && kind.minItems == null) add("Empty selection allowed")
    }.joinToString(separator = " • ").ifBlank { null }
}

private fun fieldLabel(field: ThreadUserInputField): String = if (field.required) {
    "${field.label} *"
} else {
    field.label
}

private fun formatNumberDefault(
    value: Double,
    isInteger: Boolean,
): String = if (isInteger) {
    value.toLong().toString()
} else {
    value.toString()
}

private fun String.isValidFor(format: ThreadUserInputTextFormat): Boolean = when (format) {
    ThreadUserInputTextFormat.PlainText -> true
    ThreadUserInputTextFormat.Email -> Patterns.EMAIL_ADDRESS.matcher(this).matches()
    ThreadUserInputTextFormat.Uri -> runCatching {
        val uri = Uri.parse(this)
        !uri.scheme.isNullOrBlank()
    }.getOrDefault(false)

    ThreadUserInputTextFormat.Date -> runCatching { LocalDate.parse(this) }.isSuccess
    ThreadUserInputTextFormat.DateTime -> runCatching { OffsetDateTime.parse(this) }.isSuccess ||
        runCatching { ZonedDateTime.parse(this) }.isSuccess ||
        runCatching { Instant.parse(this) }.isSuccess
}

private fun invalidTextFormatMessage(format: ThreadUserInputTextFormat): String = when (format) {
    ThreadUserInputTextFormat.PlainText -> "Invalid value"
    ThreadUserInputTextFormat.Email -> "Enter a valid email address"
    ThreadUserInputTextFormat.Uri -> "Enter a valid URL"
    ThreadUserInputTextFormat.Date -> "Use YYYY-MM-DD"
    ThreadUserInputTextFormat.DateTime -> "Use an ISO date-time value"
}

private fun ThreadUserInputTextFormat.toKeyboardType(): KeyboardType = when (this) {
    ThreadUserInputTextFormat.PlainText -> KeyboardType.Text
    ThreadUserInputTextFormat.Email -> KeyboardType.Email
    ThreadUserInputTextFormat.Uri -> KeyboardType.Uri
    ThreadUserInputTextFormat.Date,
    ThreadUserInputTextFormat.DateTime,
    -> KeyboardType.Text
}
