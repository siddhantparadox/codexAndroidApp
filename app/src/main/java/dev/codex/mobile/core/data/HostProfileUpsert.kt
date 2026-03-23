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
    val existingHost: HostProfile? = currentHosts.findHostProfileMatch(
        name = name,
        address = address,
        port = port,
        desktopId = desktopId,
    )
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

private fun List<HostProfile>.findHostProfileMatch(
    name: String,
    address: String,
    port: Int,
    desktopId: String?,
): HostProfile? {
    val trimmedDesktopId: String? = desktopId?.trim()?.takeIf(String::isNotEmpty)
    val normalizedName: String = name.trim()

    val desktopIdentityMatch: HostProfile? = trimmedDesktopId?.let { resolvedDesktopId ->
        firstOrNull { host ->
            host.desktopId == resolvedDesktopId || host.id == resolvedDesktopId
        }
    }
    if (desktopIdentityMatch != null) {
        return desktopIdentityMatch
    }

    val endpointMatch: HostProfile? = firstOrNull { host ->
        host.address == address && host.port == port
    }
    if (endpointMatch != null) {
        return endpointMatch
    }

    if (normalizedName.isBlank()) {
        return null
    }

    val sameNameMatches: List<HostProfile> = filter { host ->
        host.port == port && host.name.trim().equals(other = normalizedName, ignoreCase = true)
    }
    return sameNameMatches.singleOrNull()
}
