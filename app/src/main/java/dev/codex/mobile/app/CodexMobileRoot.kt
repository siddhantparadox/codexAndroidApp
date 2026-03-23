package dev.codex.mobile.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
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
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var notificationPermissionPrompted by rememberSaveable { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && uiState.activeHostId != null) {
            ConnectionForegroundService.start(context)
        }
    }

    LaunchedEffect(uiState.activeHostId) {
        if (uiState.activeHostId == null) {
            notificationPermissionPrompted = false
            ConnectionForegroundService.stop(context)
            return@LaunchedEffect
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED &&
            !notificationPermissionPrompted
        ) {
            notificationPermissionPrompted = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            ConnectionForegroundService.start(context)
        }
    }

    DisposableEffect(lifecycleOwner, uiState.activeHostId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START && uiState.activeHostId != null) {
                ConnectionForegroundService.start(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    CodexMobileTheme(
        useDarkTheme = when (uiState.themePreference) {
            ThemePreference.Dark -> true
            ThemePreference.Light -> false
            ThemePreference.System -> null
        },
    ) {
        CodexNavHost(
            pendingApprovals = uiState.pendingApprovals,
            alerts = uiState.alerts,
            onDismissAlert = viewModel::dismissAlert,
            onVisibleThreadChanged = viewModel::setVisibleThread,
        )
    }
}
