package dev.codex.mobile.feature.connection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionBootstrapTest {
    @Test
    fun parsesJsonBootstrapPayload() {
        val result = parseConnectionBootstrap(
            """
            {
              "version": 1,
              "desktopId": "desktop-123",
              "desktopName": "Sam's Desktop",
              "host": "192.168.1.15",
              "port": 4500
            }
            """.trimIndent(),
        )

        val bootstrap = result.getOrThrow()
        assertEquals("desktop-123", bootstrap.desktopId)
        assertEquals("Sam's Desktop", bootstrap.desktopName)
        assertEquals("192.168.1.15", bootstrap.host)
        assertEquals(4500, bootstrap.port)
        assertEquals(ConnectionBootstrapSource.QrCode, bootstrap.source)
    }

    @Test
    fun roundTripsConnectionCode() {
        val code = encodeConnectionCode(
            host = "192.168.1.15",
            port = 4500,
        )

        val bootstrap = parseConnectionBootstrap(code).getOrThrow()

        assertEquals("192.168.1.15", bootstrap.host)
        assertEquals(4500, bootstrap.port)
        assertEquals(ConnectionBootstrapSource.ConnectionCode, bootstrap.source)
    }

    @Test
    fun rejectsConnectionCodeWithInvalidChecksum() {
        val result = parseConnectionBootstrap("C0A8-010F-1194-0000")

        assertTrue(result.isFailure)
    }
}
