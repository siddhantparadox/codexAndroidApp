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
import dev.codex.mobile.core.model.ThreadDetail
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
    val activeItemIds: Set<String> = emptySet(),
    val approvals: List<ApprovalItem> = emptyList(),
    val draft: String = "",
)

class ThreadDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: CodexRepository,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<ThreadDetailRoute>()
    private val draft = MutableStateFlow("")

    init {
        viewModelScope.launch {
            repository.openThread(route.threadId)
        }
    }

    val uiState: StateFlow<ThreadDetailUiState> = combine(
        repository.observeThreadDetail(route.threadId),
        repository.observeActiveItemIds(route.threadId),
        repository.observeApprovals(),
        draft,
    ) { detail, activeItemIds, approvals, currentDraft ->
        ThreadDetailUiState(
            detail = detail,
            activeItemIds = activeItemIds,
            approvals = approvals.filter { approval -> approval.threadId == route.threadId },
            draft = currentDraft,
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
        val message = draft.value
        if (message.isBlank()) return
        draft.update { "" }
        viewModelScope.launch {
            repository.sendReply(route.threadId, message)
        }
    }

    fun interruptThread() {
        viewModelScope.launch {
            repository.interruptThread(route.threadId)
        }
    }

    fun resolveApproval(
        approvalId: String,
        decision: ApprovalDecision,
    ) {
        viewModelScope.launch {
            repository.resolveApproval(approvalId, decision)
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
