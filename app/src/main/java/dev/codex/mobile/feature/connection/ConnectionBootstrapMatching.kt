package dev.codex.mobile.feature.connection

import dev.codex.mobile.core.model.HostProfile

internal fun findKnownBootstrapHost(
    hosts: List<HostProfile>,
    bootstrap: ConnectionBootstrap,
): HostProfile? {
    val trimmedDesktopId: String? = bootstrap.desktopId?.trim()?.takeIf(String::isNotEmpty)
    val normalizedName: String = bootstrap.desktopName.trim()

    val desktopIdentityMatch: HostProfile? = trimmedDesktopId?.let { resolvedDesktopId ->
        hosts.firstOrNull { host ->
            host.desktopId == resolvedDesktopId || host.id == resolvedDesktopId
        }
    }
    if (desktopIdentityMatch != null) {
        return desktopIdentityMatch
    }

    if (normalizedName.isBlank()) {
        return null
    }

    val sameNameMatches: List<HostProfile> = hosts.filter { host ->
        host.port == bootstrap.port && host.name.trim().equals(other = normalizedName, ignoreCase = true)
    }
    return sameNameMatches.singleOrNull()
}
