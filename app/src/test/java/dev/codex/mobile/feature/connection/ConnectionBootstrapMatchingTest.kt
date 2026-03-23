package dev.codex.mobile.feature.connection

import dev.codex.mobile.core.model.HostKind
import dev.codex.mobile.core.model.HostProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectionBootstrapMatchingTest {
    @Test
    fun prefersDesktopIdentityWhenQrBootstrapMatchesKnownDesktop() {
        val hosts = listOf(
            HostProfile(
                id = "SIDDHANT",
                desktopId = "SIDDHANT",
                name = "SIDDHANT",
                address = "10.0.0.94",
                port = 4500,
                kind = HostKind.Desktop,
                isActive = true,
            ),
        )

        val match = findKnownBootstrapHost(
            hosts = hosts,
            bootstrap = ConnectionBootstrap(
                desktopId = "SIDDHANT",
                desktopName = "SIDDHANT",
                host = "192.168.1.223",
                port = 4500,
                source = ConnectionBootstrapSource.QrCode,
            ),
        )

        assertEquals("SIDDHANT", match?.id)
    }

    @Test
    fun fallsBackToUniqueDesktopNameWhenIdentityWasMissing() {
        val hosts = listOf(
            HostProfile(
                id = "legacy-host",
                name = "SIDDHANT",
                address = "10.0.0.94",
                port = 4500,
                kind = HostKind.Desktop,
                isActive = true,
            ),
        )

        val match = findKnownBootstrapHost(
            hosts = hosts,
            bootstrap = ConnectionBootstrap(
                desktopId = "SIDDHANT",
                desktopName = "SIDDHANT",
                host = "192.168.1.223",
                port = 4500,
                source = ConnectionBootstrapSource.QrCode,
            ),
        )

        assertEquals("legacy-host", match?.id)
    }

    @Test
    fun doesNotGuessWhenNameMatchIsAmbiguous() {
        val hosts = listOf(
            HostProfile(
                id = "desktop-a",
                name = "SIDDHANT",
                address = "10.0.0.94",
                port = 4500,
                kind = HostKind.Desktop,
            ),
            HostProfile(
                id = "desktop-b",
                name = "SIDDHANT",
                address = "192.168.1.223",
                port = 4500,
                kind = HostKind.Desktop,
            ),
        )

        val match = findKnownBootstrapHost(
            hosts = hosts,
            bootstrap = ConnectionBootstrap(
                desktopName = "SIDDHANT",
                host = "192.168.1.77",
                port = 4500,
                source = ConnectionBootstrapSource.QrCode,
            ),
        )

        assertNull(match)
    }
}
