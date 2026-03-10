package dev.codex.mobile.feature.approvals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.codex.mobile.core.data.CodexRepository
import dev.codex.mobile.core.model.ApprovalItem
import dev.codex.mobile.core.model.ApprovalState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ApprovalsUiState(
    val selectedTab: ApprovalState = ApprovalState.Pending,
    val approvals: List<ApprovalItem> = emptyList(),
)

class ApprovalsViewModel(
    private val repository: CodexRepository,
) : ViewModel() {
    private val selectedTab = MutableStateFlow(ApprovalState.Pending)

    val uiState: StateFlow<ApprovalsUiState> = combine(
        repository.observeApprovals(),
        selectedTab,
    ) { approvals, tab ->
        ApprovalsUiState(
            selectedTab = tab,
            approvals = approvals.filter { approval ->
                when (tab) {
                    ApprovalState.Pending -> approval.state == ApprovalState.Pending
                    ApprovalState.Approved -> approval.state == ApprovalState.Approved
                    ApprovalState.Declined -> approval.state == ApprovalState.Declined
                    ApprovalState.Archived -> approval.state == ApprovalState.Archived
                    ApprovalState.Reviewed -> approval.state == ApprovalState.Reviewed
                }
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ApprovalsUiState(),
    )

    fun selectTab(state: ApprovalState) {
        selectedTab.update { state }
    }

    fun approve(id: String) = updateApproval(id, ApprovalState.Approved)

    fun decline(id: String) = updateApproval(id, ApprovalState.Declined)

    fun archive(id: String) = updateApproval(id, ApprovalState.Archived)

    fun review(id: String) = updateApproval(id, ApprovalState.Reviewed)

    private fun updateApproval(
        id: String,
        state: ApprovalState,
    ) {
        viewModelScope.launch {
            repository.resolveApproval(id, state)
        }
    }

    companion object {
        fun factory(repository: CodexRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { ApprovalsViewModel(repository) }
        }
    }
}
