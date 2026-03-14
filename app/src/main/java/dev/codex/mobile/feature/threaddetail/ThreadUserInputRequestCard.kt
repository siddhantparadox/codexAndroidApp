package dev.codex.mobile.feature.threaddetail

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.codex.mobile.core.designsystem.component.CodexCard
import dev.codex.mobile.core.designsystem.component.StatusChip
import dev.codex.mobile.core.designsystem.theme.CodexSpacing
import dev.codex.mobile.core.model.ThreadUserInputOption
import dev.codex.mobile.core.model.ThreadUserInputQuestion
import dev.codex.mobile.core.model.ThreadUserInputRequest

private val UserInputAccent: Color = Color(0xFF3A7BD5)

@Composable
internal fun InlineUserInputRequestCard(
    request: ThreadUserInputRequest,
    onSubmit: (String, Map<String, List<String>>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedOptions = remember(request.requestId) { mutableStateMapOf<String, String>() }
    val freeTextAnswers = remember(request.requestId) { mutableStateMapOf<String, String>() }
    val answers = answersForSubmission(
        request = request,
        selectedOptions = selectedOptions,
        freeTextAnswers = freeTextAnswers,
    )
    val canSubmit = answers.size == request.questions.size && answers.isNotEmpty()

    CodexCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(CodexSpacing.cardPadding),
    ) {
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
                        text = "Clarification needed",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Codex is waiting for a short answer before it can continue this turn.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.width(CodexSpacing.listGap))
            StatusChip(
                label = "Needs Input",
                color = UserInputAccent,
            )
        }

        request.questions.forEachIndexed { index, question ->
            Spacer(modifier = Modifier.height(if (index == 0) CodexSpacing.sectionGap else CodexSpacing.listGap))
            UserInputQuestionSection(
                question = question,
                selectedOption = selectedOptions[question.id],
                freeTextAnswer = freeTextAnswers[question.id].orEmpty(),
                onSelectOption = { option ->
                    selectedOptions[question.id] = option.label
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
                    request.requestId,
                    answers,
                )
            },
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (request.questions.size == 1) {
                    "Send Answer"
                } else {
                    "Send Answers"
                },
            )
        }
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
            val isSelected = selectedOption == option.label
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectOption(option) },
                shape = MaterialTheme.shapes.small,
                color = if (isSelected) {
                    UserInputAccent.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) {
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
                        color = if (isSelected) UserInputAccent else MaterialTheme.colorScheme.onSurface,
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

private fun answersForSubmission(
    request: ThreadUserInputRequest,
    selectedOptions: Map<String, String>,
    freeTextAnswers: Map<String, String>,
): Map<String, List<String>> = buildMap {
    request.questions.forEach { question ->
        val value = resolvedAnswer(
            question = question,
            selectedOption = selectedOptions[question.id],
            freeTextAnswer = freeTextAnswers[question.id],
        )
        if (value != null) {
            put(question.id, listOf(value))
        }
    }
}

private fun resolvedAnswer(
    question: ThreadUserInputQuestion,
    selectedOption: String?,
    freeTextAnswer: String?,
): String? = when {
    !selectedOption.isNullOrBlank() -> selectedOption
    question.options.isEmpty() || question.isOtherAllowed -> freeTextAnswer
        ?.trim()
        ?.takeIf { answer -> answer.isNotBlank() }
    else -> null
}
