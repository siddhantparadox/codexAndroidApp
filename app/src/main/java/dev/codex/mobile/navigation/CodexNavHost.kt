package dev.codex.mobile.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.codex.mobile.app.InAppThreadNotificationHost
import dev.codex.mobile.core.designsystem.component.CodexBottomBar
import dev.codex.mobile.core.designsystem.component.TopLevelDestination
import dev.codex.mobile.core.model.InAppThreadNotification
import dev.codex.mobile.core.util.AppLog
import dev.codex.mobile.feature.approvals.ApprovalsScreen
import dev.codex.mobile.feature.connection.HostConnectionScreen
import dev.codex.mobile.feature.dashboard.DashboardScreen
import dev.codex.mobile.feature.settings.SettingsScreen
import dev.codex.mobile.feature.threaddetail.ThreadDetailScreen
import dev.codex.mobile.feature.threads.ThreadsScreen
import dev.codex.mobile.feature.usage.UsageWrappedScreen

@Composable
fun CodexNavHost(
    pendingApprovals: Int,
    notifications: List<InAppThreadNotification>,
    onDismissNotification: (String) -> Unit,
    onVisibleThreadChanged: (String?) -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentThreadId: String? = if (currentDestination?.hasRoute<ThreadDetailRoute>() == true) {
        backStackEntry?.toRoute<ThreadDetailRoute>()?.threadId
    } else {
        null
    }
    val currentScreen = when {
        currentDestination?.hasRoute<DashboardRoute>() == true -> "dashboard"
        currentDestination?.hasRoute<ThreadsRoute>() == true -> "threads"
        currentDestination?.hasRoute<ApprovalsRoute>() == true -> "approvals"
        currentDestination?.hasRoute<SettingsRoute>() == true -> "settings"
        currentDestination?.hasRoute<HostConnectionRoute>() == true -> "host_connection"
        currentDestination?.hasRoute<UsageWrappedRoute>() == true -> "usage_wrapped"
        currentDestination?.hasRoute<ThreadDetailRoute>() == true -> "thread_detail"
        else -> null
    }
    val topLevelDestination = when {
        currentDestination?.hasRoute<DashboardRoute>() == true -> TopLevelDestination.Dashboard
        currentDestination?.hasRoute<ThreadsRoute>() == true -> TopLevelDestination.Threads
        currentDestination?.hasRoute<ApprovalsRoute>() == true -> TopLevelDestination.Approvals
        else -> TopLevelDestination.Settings
    }
    val isOnTopLevelDestination = when {
        currentDestination?.hasRoute<DashboardRoute>() == true -> true
        currentDestination?.hasRoute<ThreadsRoute>() == true -> true
        currentDestination?.hasRoute<ApprovalsRoute>() == true -> true
        currentDestination?.hasRoute<SettingsRoute>() == true -> true
        else -> false
    }
    val showBottomBar = currentDestination?.hasRoute<ThreadDetailRoute>() != true &&
        currentDestination?.hasRoute<UsageWrappedRoute>() != true

    LaunchedEffect(currentScreen) {
        currentScreen?.let(AppLog::screen)
    }

    LaunchedEffect(currentThreadId) {
        onVisibleThreadChanged(currentThreadId)
    }

    LaunchedEffect(currentThreadId, notifications) {
        val visibleThreadId: String = currentThreadId ?: return@LaunchedEffect
        notifications
            .filter { notification -> notification.threadId == visibleThreadId }
            .forEach { notification -> onDismissNotification(notification.id) }
    }

    fun navigateToTopLevelDestination(destination: TopLevelDestination) {
        AppLog.action(name = "bottom_nav_select", detail = destination.name)
        val route = when (destination) {
            TopLevelDestination.Dashboard -> DashboardRoute
            TopLevelDestination.Threads -> ThreadsRoute
            TopLevelDestination.Approvals -> ApprovalsRoute
            TopLevelDestination.Settings -> SettingsRoute
        }
        navController.navigateToTopLevel(route)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom),
            bottomBar = {
                if (showBottomBar) {
                    CodexBottomBar(
                        currentDestination = topLevelDestination,
                        pendingApprovals = pendingApprovals,
                        onDestinationSelected = ::navigateToTopLevelDestination,
                    )
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = DashboardRoute,
                modifier = Modifier.padding(if (showBottomBar) innerPadding else PaddingValues()),
            ) {
                composable<DashboardRoute> {
                    DashboardScreen(
                        onOpenThreads = {
                            AppLog.action(name = "open_threads", detail = "from_dashboard")
                            navigateToTopLevelDestination(TopLevelDestination.Threads)
                        },
                        onOpenHostConnection = {
                            AppLog.action(name = "open_settings", detail = "from_dashboard_connection")
                            navigateToTopLevelDestination(TopLevelDestination.Settings)
                        },
                        onOpenThread = { threadId ->
                            AppLog.action(name = "open_thread", detail = threadId)
                            navController.navigate(ThreadDetailRoute(threadId = threadId))
                        },
                        onOpenUsageWrapped = {
                            AppLog.action(name = "open_usage_wrapped", detail = "from_dashboard")
                            navController.navigate(UsageWrappedRoute)
                        },
                    )
                }
                composable<ThreadsRoute> {
                    ThreadsScreen(
                        onOpenThread = { threadId ->
                            AppLog.action(name = "open_thread", detail = threadId)
                            navController.navigate(ThreadDetailRoute(threadId = threadId))
                        },
                    )
                }
                composable<ThreadDetailRoute> {
                    ThreadDetailScreen(
                        onNavigateBack = {
                            AppLog.action(name = "navigate_back", detail = "thread_detail")
                            navController.popBackStack()
                        },
                    )
                }
                composable<ApprovalsRoute> {
                    ApprovalsScreen(
                        onOpenThread = { threadId ->
                            AppLog.action(name = "open_thread_from_approval", detail = threadId)
                            navController.navigate(ThreadDetailRoute(threadId = threadId))
                        },
                    )
                }
                composable<SettingsRoute> {
                    SettingsScreen(
                        onOpenHostConnection = {
                            AppLog.action(name = "open_host_connection", detail = "from_settings")
                            navController.navigate(HostConnectionRoute)
                        },
                    )
                }
                composable<HostConnectionRoute> {
                    HostConnectionScreen(
                        onNavigateBack = {
                            AppLog.action(name = "navigate_back", detail = "host_connection")
                            navController.popBackStack()
                        },
                    )
                }
                composable<UsageWrappedRoute> {
                    UsageWrappedScreen(
                        onNavigateBack = {
                            AppLog.action(name = "navigate_back", detail = "usage_wrapped")
                            navController.popBackStack()
                        },
                    )
                }
            }
        }

        InAppThreadNotificationHost(
            notifications = notifications,
            onDismissNotification = onDismissNotification,
            onOpenThread = { threadId ->
                AppLog.action(name = "open_thread_from_notification", detail = threadId)
                navController.navigate(ThreadDetailRoute(threadId = threadId))
            },
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

private fun NavHostController.navigateToTopLevel(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
