package dev.codex.mobile.core.data

import dev.codex.mobile.core.model.HostKind
import dev.codex.mobile.core.model.HostProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HostProfileUpsertTest {
    @Test
    fun reusesDesktopIdentityAndUpdatesAddress() {
        val initialHosts = listOf(
            HostProfile(
                id = "desktop-123",
                desktopId = "desktop-123",
                name = "Studio Mac",
                address = "192.168.1.55",
                port = 4500,
                kind = HostKind.Laptop,
                isActive = false,
            ),
        )

        val result = upsertHostProfile(
            currentHosts = initialHosts,
            generatedId = "desktop-123",
            name = "Studio Mac",
            address = "192.168.1.88",
            port = 4500,
            kind = HostKind.Laptop,
            desktopId = "desktop-123",
            activate = true,
        )

        assertEquals("desktop-123", result.hostId)
        assertEquals(1, result.hosts.size)
        assertEquals("192.168.1.88", result.hosts.single().address)
        assertTrue(result.hosts.single().isActive)
    }

    @Test
    fun clearsPreviousActiveHostWhenActivatingNewDesktop() {
        val initialHosts = listOf(
            HostProfile(
                id = "desktop-a",
                desktopId = "desktop-a",
                name = "Desk A",
                address = "192.168.1.10",
                port = 4500,
                kind = HostKind.Desktop,
                isActive = true,
            ),
            HostProfile(
                id = "desktop-b",
                desktopId = "desktop-b",
                name = "Desk B",
                address = "192.168.1.11",
                port = 4500,
                kind = HostKind.Desktop,
                isActive = false,
            ),
        )

        val result = upsertHostProfile(
            currentHosts = initialHosts,
            generatedId = "desktop-b",
            name = "Desk B",
            address = "192.168.1.11",
            port = 4500,
            kind = HostKind.Desktop,
            desktopId = "desktop-b",
            activate = true,
        )

        assertTrue(result.hosts.single { it.id == "desktop-b" }.isActive)
        assertTrue(result.hosts.single { it.id == "desktop-a" }.isActive.not())
    }

    @Test
    fun updatesUniqueSameNamedHostWhenDesktopIdentityWasMissing() {
        val initialHosts = listOf(
            HostProfile(
                id = "legacy-host",
                name = "SIDDHANT",
                address = "10.0.0.94",
                port = 4500,
                kind = HostKind.Desktop,
                isActive = true,
            ),
        )

        val result = upsertHostProfile(
            currentHosts = initialHosts,
            generatedId = "SIDDHANT",
            name = "SIDDHANT",
            address = "192.168.1.223",
            port = 4500,
            kind = HostKind.Desktop,
            desktopId = "SIDDHANT",
            activate = true,
        )

        assertEquals("legacy-host", result.hostId)
        assertEquals(1, result.hosts.size)
        assertEquals("192.168.1.223", result.hosts.single().address)
        assertEquals("SIDDHANT", result.hosts.single().desktopId)
        assertTrue(result.hosts.single().isActive)
    }
}
