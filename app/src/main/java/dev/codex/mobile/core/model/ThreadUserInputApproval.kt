package dev.codex.mobile.core.model

internal data class ToolApprovalAction(
    val label: String,
    val response: ThreadUserInputResponse,
    val answerValue: String,
)

internal data class ToolApprovalPrompt(
    val header: String,
    val prompt: String,
    val actions: List<ToolApprovalAction>,
    val questionId: String,
)

internal val ThreadUserInputRequest.approvalPrompt: ToolApprovalPrompt?
    get() {
        val toolQuestions = payload as? ThreadUserInputPayload.ToolQuestions ?: return null
        if (toolQuestions.questions.size != 1) return null

        val question = toolQuestions.questions.single()
        if (question.isOtherAllowed || question.isSecret) return null

        val options = question.options
        if (options.size !in 2..3) return null

        val actionsBySemantic = linkedMapOf<ToolApprovalSemantic, ToolApprovalAction>()
        for (option in options) {
            val semantic = option.label.toApprovalSemantic() ?: return null
            if (semantic in actionsBySemantic) return null
            actionsBySemantic[semantic] = ToolApprovalAction(
                label = option.label,
                response = semantic.toResponse(),
                answerValue = option.value,
            )
        }

        if (ToolApprovalSemantic.Accept !in actionsBySemantic) return null
        if (
            ToolApprovalSemantic.Decline !in actionsBySemantic &&
            ToolApprovalSemantic.Cancel !in actionsBySemantic
        ) {
            return null
        }

        return ToolApprovalPrompt(
            header = question.header,
            prompt = question.prompt,
            actions = listOfNotNull(
                actionsBySemantic[ToolApprovalSemantic.Accept],
                actionsBySemantic[ToolApprovalSemantic.Decline],
                actionsBySemantic[ToolApprovalSemantic.Cancel],
            ),
            questionId = question.id,
        )
    }

internal val ThreadUserInputRequest.isApprovalPrompt: Boolean
    get() = approvalPrompt != null

internal fun ThreadUserInputRequest.approvalAnswersFor(
    response: ThreadUserInputResponse,
): Map<String, List<String>>? {
    val prompt = approvalPrompt ?: return null
    val answerValue = prompt.actions.firstOrNull { action -> action.response == response }
        ?.answerValue
        ?: return null
    return mapOf(prompt.questionId to listOf(answerValue))
}

private enum class ToolApprovalSemantic {
    Accept,
    Decline,
    Cancel,
}

private fun String.toApprovalSemantic(): ToolApprovalSemantic? = when (normalizedApprovalToken()) {
    "accept",
    "allow",
    "approve",
    -> ToolApprovalSemantic.Accept

    "decline",
    "deny",
    "reject",
    "disallow",
    "dontallow",
    -> ToolApprovalSemantic.Decline

    "cancel" -> ToolApprovalSemantic.Cancel
    else -> null
}

private fun String.normalizedApprovalToken(): String = lowercase()
    .filter(Char::isLetter)

private fun ToolApprovalSemantic.toResponse(): ThreadUserInputResponse = when (this) {
    ToolApprovalSemantic.Accept -> ThreadUserInputResponse.Accept()
    ToolApprovalSemantic.Decline -> ThreadUserInputResponse.Decline
    ToolApprovalSemantic.Cancel -> ThreadUserInputResponse.Cancel
}
