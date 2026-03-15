package dev.codex.mobile.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class HostKind {
    Laptop,
    Desktop,
}

@Serializable
data class HostProfile(
    val id: String,
    val desktopId: String? = null,
    val name: String,
    val address: String,
    val port: Int,
    val kind: HostKind,
    val isActive: Boolean = false,
)
