package dev.codex.mobile.core.data

import dev.codex.mobile.core.model.HostKind
import dev.codex.mobile.core.model.HostProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostProfileMutationsTest {
    @Test
    fun renameHostProfileTrimsAndUpdatesMatchingHost() {
        val hosts = listOf(
            HostProfile(
                id = "desktop-1",
                name = "Home Desktop",
                address = "192.168.1.42",
                port = 4500,
                kind = HostKind.Desktop,
            ),
        )

        val updatedHosts = renameHostProfile(
            currentHosts = hosts,
            hostId = "desktop-1",
            name = "  Studio Mac  ",
        )

        assertNotNull(updatedHosts)
        assertEquals("Studio Mac", updatedHosts!!.single().name)
    }

    @Test
    fun renameHostProfileRejectsBlankNames() {
        val hosts = listOf(
            HostProfile(
                id = "desktop-1",
                name = "Home Desktop",
                address = "192.168.1.42",
                port = 4500,
                kind = HostKind.Desktop,
            ),
        )

        val updatedHosts = renameHostProfile(
            currentHosts = hosts,
            hostId = "desktop-1",
            name = "   ",
        )

        assertNull(updatedHosts)
    }

    @Test
    fun removeHostProfileRemovesOnlySavedHost() {
        val hosts = listOf(
            HostProfile(
                id = "desktop-1",
                name = "Home Desktop",
                address = "192.168.1.42",
                port = 4500,
                kind = HostKind.Desktop,
                isActive = true,
            ),
        )

        val removalResult = removeHostProfile(
            currentHosts = hosts,
            hostId = "desktop-1",
        )

        assertTrue(removalResult.hosts.isEmpty())
        assertEquals("desktop-1", removalResult.removedHost?.id)
    }

    @Test
    fun removeHostProfileDoesNotFallbackToAnotherSavedHost() {
        val hosts = listOf(
            HostProfile(
                id = "desktop-1",
                name = "Work Desktop",
                address = "10.0.0.5",
                port = 4500,
                kind = HostKind.Desktop,
                isActive = true,
            ),
            HostProfile(
                id = "desktop-2",
                name = "Studio Mac",
                address = "10.0.0.8",
                port = 4500,
                kind = HostKind.Laptop,
            ),
        )

        val removalResult = removeHostProfile(
            currentHosts = hosts,
            hostId = "desktop-1",
        )

        assertEquals("desktop-1", removalResult.removedHost?.id)
        assertEquals(1, removalResult.hosts.size)
        assertEquals("desktop-2", removalResult.hosts.single().id)
        assertFalse(removalResult.hosts.single().isActive)
    }
}
