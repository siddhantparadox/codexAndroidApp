package dev.codex.mobile.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.codex.mobile.core.designsystem.theme.CodexMobileTheme
import dev.codex.mobile.core.model.ThemePreference
import dev.codex.mobile.navigation.CodexNavHost

@Composable
fun CodexMobileRoot(
    viewModel: CodexRootViewModel = viewModel(
        factory = CodexRootViewModel.factory(CodexAppGraph.repository),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CodexMobileTheme(
        useDarkTheme = when (uiState.themePreference) {
            ThemePreference.Dark -> true
            ThemePreference.Light -> false
            ThemePreference.System -> null
        },
    ) {
        CodexNavHost(
            pendingApprovals = uiState.pendingApprovals,
            notifications = uiState.notifications,
            onDismissNotification = viewModel::dismissThreadNotification,
            onVisibleThreadChanged = viewModel::setVisibleThread,
        )
    }
}
