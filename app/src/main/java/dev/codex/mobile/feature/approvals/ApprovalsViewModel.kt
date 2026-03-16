package dev.codex.mobile.feature.approvals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.codex.mobile.core.data.CodexRepository
import dev.codex.mobile.core.model.ApprovalDecision
import dev.codex.mobile.core.model.ThreadUserInputResponse
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ApprovalsUiState(
    val entries: List<ApprovalQueueEntry> = emptyList(),
)

class ApprovalsViewModel(
    private val repository: CodexRepository,
) : ViewModel() {
    val uiState: StateFlow<ApprovalsUiState> = combine(
        repository.observeApprovals(),
        repository.observeUserInputRequests(),
    ) { approvals, userInputRequests ->
        ApprovalsUiState(
            entries = buildApprovalQueueEntries(
                approvals = approvals,
                userInputRequests = userInputRequests,
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ApprovalsUiState(),
    )

    fun resolveApproval(
        id: String,
        decision: ApprovalDecision,
    ) {
        viewModelScope.launch {
            repository.resolveApproval(id, decision)
        }
    }

    fun respondToUserInput(
        requestId: String,
        response: ThreadUserInputResponse,
    ) {
        viewModelScope.launch {
            repository.respondToUserInput(requestId, response)
        }
    }

    companion object {
        fun factory(repository: CodexRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { ApprovalsViewModel(repository) }
        }
    }
}
