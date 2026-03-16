package dev.codex.mobile.core.data

import dev.codex.mobile.core.model.HostProfile

internal data class HostProfileRemovalResult(
    val hosts: List<HostProfile>,
    val removedHost: HostProfile?,
)

internal fun renameHostProfile(
    currentHosts: List<HostProfile>,
    hostId: String,
    name: String,
): List<HostProfile>? {
    val trimmedName = name.trim()
    if (trimmedName.isEmpty()) return null

    var renamed = false
    val updatedHosts = currentHosts.map { host ->
        if (host.id != hostId) {
            host
        } else {
            renamed = true
            host.copy(name = trimmedName)
        }
    }

    return updatedHosts.takeIf { renamed }
}

internal fun removeHostProfile(
    currentHosts: List<HostProfile>,
    hostId: String,
): HostProfileRemovalResult {
    val removedHost = currentHosts.firstOrNull { host -> host.id == hostId }
    return HostProfileRemovalResult(
        hosts = currentHosts.filterNot { host -> host.id == hostId },
        removedHost = removedHost,
    )
}
