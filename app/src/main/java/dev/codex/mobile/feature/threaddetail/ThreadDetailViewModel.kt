package dev.codex.mobile.feature.threaddetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import dev.codex.mobile.core.data.CodexRepository
import dev.codex.mobile.core.model.ApprovalDecision
import dev.codex.mobile.core.model.ApprovalItem
import dev.codex.mobile.core.model.ComposerCatalog
import dev.codex.mobile.core.model.ComposerImageAttachment
import dev.codex.mobile.core.model.ComposerModelOption
import dev.codex.mobile.core.model.ComposerPersonality
import dev.codex.mobile.core.model.ComposerReasoningEffort
import dev.codex.mobile.core.model.ComposerSandboxMode
import dev.codex.mobile.core.model.ComposerSkillOption
import dev.codex.mobile.core.model.ConnectionState
import dev.codex.mobile.core.model.ThreadDetail
import dev.codex.mobile.core.model.ThreadDynamicToolRequest
import dev.codex.mobile.core.model.ThreadDynamicToolResponse
import dev.codex.mobile.core.model.ThreadReplyRequest
import dev.codex.mobile.core.model.ThreadUserInputRequest
import dev.codex.mobile.core.model.ThreadUserInputResponse
import dev.codex.mobile.core.model.isActive
import dev.codex.mobile.core.model.isConnected
import dev.codex.mobile.navigation.ThreadDetailRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ThreadDetailUiState(
    val detail: ThreadDetail? = null,
    val isInitialLoadInFlight: Boolean = true,
    val activeItemIds: Set<String> = emptySet(),
    val approvals: List<ApprovalItem> = emptyList(),
    val userInputRequests: List<ThreadUserInputRequest> = emptyList(),
    val dynamicToolRequests: List<ThreadDynamicToolRequest> = emptyList(),
    val draft: String = "",
    val canInterrupt: Boolean = false,
    val isInterrupting: Boolean = false,
    val composerCatalog: ComposerCatalog = ComposerCatalog(),
    val selectedModel: ComposerModelOption? = null,
    val selectedEffort: ComposerReasoningEffort = ComposerReasoningEffort.Medium,
    val selectedPersonality: ComposerPersonality = ComposerPersonality.Default,
    val selectedSandboxMode: ComposerSandboxMode = ComposerSandboxMode.Default,
    val selectedSkill: ComposerSkillOption? = null,
    val selectedImage: ComposerImageAttachment? = null,
    val sendEnabled: Boolean = false,
)

private data class ComposerSelectionState(
    val catalog: ComposerCatalog = ComposerCatalog(),
    val selectedModel: ComposerModelOption? = null,
    val selectedEffort: ComposerReasoningEffort = ComposerReasoningEffort.Medium,
    val selectedPersonality: ComposerPersonality = ComposerPersonality.Default,
    val selectedSandboxMode: ComposerSandboxMode = ComposerSandboxMode.Default,
    val selectedSkill: ComposerSkillOption? = null,
    val selectedImage: ComposerImageAttachment? = null,
)

private data class ComposerSelectionInputs(
    val currentEffort: ComposerReasoningEffort? = null,
    val personality: ComposerPersonality = ComposerPersonality.Default,
    val sandboxMode: ComposerSandboxMode = ComposerSandboxMode.Default,
    val skill: ComposerSkillOption? = null,
    val image: ComposerImageAttachment? = null,
)

private data class ThreadBaseUiState(
    val detail: ThreadDetail? = null,
    val isInitialLoadInFlight: Boolean = true,
    val activeItemIds: Set<String> = emptySet(),
    val approvals: List<ApprovalItem> = emptyList(),
    val userInputRequests: List<ThreadUserInputRequest> = emptyList(),
    val dynamicToolRequests: List<ThreadDynamicToolRequest> = emptyList(),
    val draft: String = "",
    val isConnected: Boolean = false,
    val canInterrupt: Boolean = false,
    val isInterrupting: Boolean = false,
)

private data class ThreadLoadState(
    val detail: ThreadDetail? = null,
    val isInitialLoadInFlight: Boolean = true,
)

private data class ThreadInteractionState(
    val connection: ConnectionState = ConnectionState(),
    val draft: String = "",
    val interruptInFlight: Boolean = false,
)

class ThreadDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: CodexRepository,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<ThreadDetailRoute>()
    private val draft = MutableStateFlow("")
    private val initialLoadInFlight = MutableStateFlow(true)
    private val interruptRequested = MutableStateFlow(false)
    private val selectedModelId = MutableStateFlow<String?>(null)
    private val selectedEffort = MutableStateFlow<ComposerReasoningEffort?>(null)
    private val selectedPersonality = MutableStateFlow(ComposerPersonality.Default)
    private val selectedSandboxMode = MutableStateFlow(ComposerSandboxMode.Default)
    private val selectedSkill = MutableStateFlow<ComposerSkillOption?>(null)
    private val selectedImage = MutableStateFlow<ComposerImageAttachment?>(null)

    init {
        viewModelScope.launch {
            try {
                repository.openThread(route.threadId)
            } finally {
                initialLoadInFlight.update { false }
            }
        }
        viewModelScope.launch {
            repository.refreshComposerCatalog()
        }
        viewModelScope.launch {
            repository.observeThreadDetail(route.threadId).collect { detail ->
                if (detail?.summary?.status?.isActive != true) {
                    interruptRequested.update { false }
                }
            }
        }
    }

    private val composerSelectionInputs = combine(
        selectedEffort,
        selectedPersonality,
        selectedSandboxMode,
        selectedSkill,
        selectedImage,
    ) { currentEffort, personality, sandboxMode, skill, image ->
        ComposerSelectionInputs(
            currentEffort = currentEffort,
            personality = personality,
            sandboxMode = sandboxMode,
            skill = skill,
            image = image,
        )
    }

    private val composerSelection = combine(
        repository.observeComposerCatalog(),
        repository.observeThreadDetail(route.threadId),
        selectedModelId,
        composerSelectionInputs,
    ) { catalog, detail, currentModelId, inputs ->
        val resolvedModelId = currentModelId ?: detail?.summary?.currentModelId
        val selectedModel = catalog.models.firstOrNull { it.id == resolvedModelId }
            ?: catalog.models.firstOrNull { it.isDefault }
            ?: catalog.models.firstOrNull()
        val resolvedEffort = inputs.currentEffort
            ?.takeIf { effort -> selectedModel?.supportedReasoningEfforts?.any { it.effort == effort } != false }
            ?: detail?.summary?.currentReasoningEffort
                ?.takeIf { effort -> selectedModel?.supportedReasoningEfforts?.any { it.effort == effort } != false }
            ?: selectedModel?.defaultReasoningEffort
            ?: ComposerReasoningEffort.Medium
        ComposerSelectionState(
            catalog = catalog,
            selectedModel = selectedModel,
            selectedEffort = resolvedEffort,
            selectedPersonality = inputs.personality,
            selectedSandboxMode = inputs.sandboxMode,
            selectedSkill = inputs.skill,
            selectedImage = inputs.image,
        )
    }

    private val threadLoadState = combine(
        repository.observeThreadDetail(route.threadId),
        initialLoadInFlight,
    ) { detail, isInitialLoadInFlight ->
        ThreadLoadState(
            detail = detail,
            isInitialLoadInFlight = isInitialLoadInFlight,
        )
    }

    private val pendingRequestState = combine(
        repository.observeApprovals(),
        repository.observeUserInputRequests(),
        repository.observeDynamicToolRequests(),
    ) { approvals, userInputRequests, dynamicToolRequests ->
        Triple(approvals, userInputRequests, dynamicToolRequests)
    }

    private val interactionState = combine(
        repository.observeConnection(),
        draft,
        interruptRequested,
    ) { connection, currentDraft, interruptInFlight ->
        ThreadInteractionState(
            connection = connection,
            draft = currentDraft,
            interruptInFlight = interruptInFlight,
        )
    }

    private val baseUiState = combine(
        threadLoadState,
        repository.observeActiveItemIds(route.threadId),
        pendingRequestState,
        interactionState,
    ) { threadLoad, activeItemIds, pendingRequests, interaction ->
        val approvals = pendingRequests.first
        val userInputRequests = pendingRequests.second
        val dynamicToolRequests = pendingRequests.third
        val detail = threadLoad.detail
        val canInterrupt = interaction.connection.isConnected && detail?.summary?.status?.isActive == true
        ThreadBaseUiState(
            detail = detail,
            isInitialLoadInFlight = threadLoad.isInitialLoadInFlight,
            activeItemIds = activeItemIds,
            approvals = approvals.filter { approval -> approval.threadId == route.threadId },
            userInputRequests = userInputRequests.filter { request -> request.threadId == route.threadId },
            dynamicToolRequests = dynamicToolRequests.filter { request -> request.threadId == route.threadId },
            draft = interaction.draft,
            isConnected = interaction.connection.isConnected,
            canInterrupt = canInterrupt,
            isInterrupting = canInterrupt && interaction.interruptInFlight,
        )
    }

    val uiState: StateFlow<ThreadDetailUiState> = combine(
        baseUiState,
        composerSelection,
    ) { base, composer ->
        ThreadDetailUiState(
            detail = base.detail,
            isInitialLoadInFlight = base.isInitialLoadInFlight,
            activeItemIds = base.activeItemIds,
            approvals = base.approvals,
            userInputRequests = base.userInputRequests,
            dynamicToolRequests = base.dynamicToolRequests,
            draft = base.draft,
            canInterrupt = base.canInterrupt,
            isInterrupting = base.isInterrupting,
            composerCatalog = composer.catalog,
            selectedModel = composer.selectedModel,
            selectedEffort = composer.selectedEffort,
            selectedPersonality = composer.selectedPersonality,
            selectedSandboxMode = composer.selectedSandboxMode,
            selectedSkill = composer.selectedSkill,
            selectedImage = composer.selectedImage,
            sendEnabled = base.isConnected && (base.draft.isNotBlank() || composer.selectedSkill != null || composer.selectedImage != null),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ThreadDetailUiState(),
    )

    fun onDraftChanged(value: String) {
        draft.update { value }
    }

    fun sendReply() {
        val request = ThreadReplyRequest(
            message = draft.value,
            modelId = selectedModelId.value,
            reasoningEffort = selectedEffort.value,
            personality = if (uiState.value.selectedModel?.supportsPersonality == true) {
                uiState.value.selectedPersonality
            } else {
                ComposerPersonality.Default
            },
            sandboxMode = uiState.value.selectedSandboxMode,
            skill = uiState.value.selectedSkill,
            image = uiState.value.selectedImage,
        )
        if (!request.hasPayload) return
        draft.update { "" }
        selectedSkill.update { null }
        selectedImage.update { null }
        viewModelScope.launch {
            repository.sendReply(route.threadId, request)
            selectedModelId.update { null }
            selectedEffort.update { null }
        }
    }

    fun interruptThread() {
        if (interruptRequested.value) return
        interruptRequested.update { true }
        viewModelScope.launch {
            runCatching {
                repository.interruptThread(route.threadId)
            }.onFailure {
                interruptRequested.update { false }
            }
        }
    }

    fun selectModel(modelId: String) {
        selectedModelId.update { modelId }
        val model = uiState.value.composerCatalog.models.firstOrNull { it.id == modelId } ?: return
        selectedEffort.update { effort ->
            effort?.takeIf { current ->
                model.supportedReasoningEfforts.any { it.effort == current }
            } ?: model.defaultReasoningEffort
        }
        if (!model.supportsPersonality) {
            selectedPersonality.update { ComposerPersonality.Default }
        }
    }

    fun selectEffort(effort: ComposerReasoningEffort) {
        selectedEffort.update { effort }
    }

    fun selectPersonality(personality: ComposerPersonality) {
        selectedPersonality.update { personality }
    }

    fun selectSandboxMode(sandboxMode: ComposerSandboxMode) {
        selectedSandboxMode.update { sandboxMode }
    }

    fun clearSandboxMode() {
        selectedSandboxMode.update { ComposerSandboxMode.Default }
    }

    fun selectSkill(skill: ComposerSkillOption) {
        selectedSkill.update { skill }
        draft.update { current -> current.removeSuffix("$").trimEnd() }
    }

    fun clearSkill() {
        selectedSkill.update { null }
    }

    fun attachImage(contentUri: String) {
        selectedImage.update { ComposerImageAttachment(contentUri = contentUri) }
    }

    fun clearImage() {
        selectedImage.update { null }
    }

    fun resolveApproval(
        approvalId: String,
        decision: ApprovalDecision,
    ) {
        viewModelScope.launch {
            repository.resolveApproval(approvalId, decision)
        }
    }

    fun respondToUserInput(
        requestId: String,
        response: ThreadUserInputResponse,
    ) {
        viewModelScope.launch {
            repository.respondToUserInput(
                requestId = requestId,
                response = response,
            )
        }
    }

    fun respondToDynamicTool(
        requestId: String,
        response: ThreadDynamicToolResponse,
    ) {
        viewModelScope.launch {
            repository.respondToDynamicTool(
                requestId = requestId,
                response = response,
            )
        }
    }

    companion object {
        fun factory(repository: CodexRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ThreadDetailViewModel(
                    savedStateHandle = createSavedStateHandle(),
                    repository = repository,
                )
            }
        }
    }
}

