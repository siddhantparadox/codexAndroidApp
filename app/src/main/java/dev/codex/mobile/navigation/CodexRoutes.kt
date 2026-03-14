package dev.codex.mobile.navigation

import kotlinx.serialization.Serializable

@Serializable
object DashboardRoute

@Serializable
object ThreadsRoute

@Serializable
data class ThreadDetailRoute(val threadId: String)

@Serializable
object ApprovalsRoute

@Serializable
object SettingsRoute

@Serializable
object HostConnectionRoute

@Serializable
object UsageWrappedRoute
