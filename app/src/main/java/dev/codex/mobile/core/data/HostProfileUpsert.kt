package dev.codex.mobile.core.data

import dev.codex.mobile.core.model.HostKind
import dev.codex.mobile.core.model.HostProfile

internal data class HostProfileUpsertResult(
    val hosts: List<HostProfile>,
    val hostId: String,
)

internal fun upsertHostProfile(
    currentHosts: List<HostProfile>,
    generatedId: String,
    name: String,
    address: String,
    port: Int,
    kind: HostKind,
    desktopId: String?,
    activate: Boolean,
): HostProfileUpsertResult {
    val existingHost = currentHosts.firstOrNull { host ->
        (desktopId != null && host.desktopId == desktopId) ||
            (host.address == address && host.port == port)
    }
    val hostId = existingHost?.id ?: generatedId
    val updatedHost = HostProfile(
        id = hostId,
        desktopId = desktopId ?: existingHost?.desktopId,
        name = name,
        address = address,
        port = port,
        kind = kind,
        isActive = activate || existingHost?.isActive == true,
    )
    val updatedHosts = currentHosts
        .filterNot { host -> host.id == existingHost?.id }
        .map { host ->
            if (activate) host.copy(isActive = false) else host
        } + updatedHost

    return HostProfileUpsertResult(
        hosts = updatedHosts,
        hostId = hostId,
    )
}
