package dev.codex.mobile.core.model

enum class HostKind {
    Laptop,
    Desktop,
}

data class HostProfile(
    val id: String,
    val name: String,
    val address: String,
    val port: Int,
    val kind: HostKind,
    val isActive: Boolean = false,
    val qualityLabel: String = "Excellent",
)
