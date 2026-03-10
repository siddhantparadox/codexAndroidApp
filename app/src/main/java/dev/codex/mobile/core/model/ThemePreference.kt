package dev.codex.mobile.core.model

enum class ThemePreference {
    Light,
    Dark,
    System,
}

data class AppPreferences(
    val themePreference: ThemePreference = ThemePreference.System,
    val connectionAlerts: Boolean = true,
)
