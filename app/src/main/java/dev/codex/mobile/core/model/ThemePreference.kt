package dev.codex.mobile.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ThemePreference {
    Light,
    Dark,
    System,
}

@Serializable
data class AppPreferences(
    val themePreference: ThemePreference = ThemePreference.System,
    val connectionAlerts: Boolean = true,
)
