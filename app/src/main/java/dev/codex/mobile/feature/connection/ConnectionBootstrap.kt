package dev.codex.mobile.feature.connection

import java.net.URI
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class ConnectionBootstrap(
    val desktopId: String? = null,
    val desktopName: String,
    val host: String,
    val port: Int,
    val source: ConnectionBootstrapSource,
)

enum class ConnectionBootstrapSource {
    QrCode,
    ConnectionCode,
    WebSocketUrl,
}

@Serializable
private data class ConnectionBootstrapPayload(
    @SerialName("version")
    val version: Int = 1,
    @SerialName("desktopId")
    val desktopId: String? = null,
    @SerialName("desktopName")
    val desktopName: String? = null,
    @SerialName("host")
    val host: String,
    @SerialName("port")
    val port: Int,
)

private val bootstrapJson: Json = Json {
    ignoreUnknownKeys = true
}

internal fun parseConnectionBootstrap(input: String): Result<ConnectionBootstrap> {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) {
        return Result.failure(IllegalArgumentException("Enter a connection code or scan a QR code."))
    }

    return runCatching {
        when {
            trimmed.startsWith("{") -> parseJsonBootstrap(trimmed)
            trimmed.startsWith("ws://", ignoreCase = true) -> parseWebSocketBootstrap(trimmed)
            else -> parseConnectionCode(trimmed)
        }
    }
}

internal fun encodeConnectionCode(
    host: String,
    port: Int,
): String {
    val ipSegments = host.split('.')
    require(ipSegments.size == 4) { "Only IPv4 addresses can be encoded." }
    val bytes = ByteArray(8)
    ipSegments.forEachIndexed { index, value ->
        val segment = value.toInt()
        require(segment in 0..255) { "Invalid IPv4 address segment." }
        bytes[index] = segment.toByte()
    }
    require(port in 1..65535) { "Invalid port." }
    bytes[4] = ((port ushr 8) and 0xFF).toByte()
    bytes[5] = (port and 0xFF).toByte()
    val checksum = connectionCodeChecksum(bytes, length = 6)
    bytes[6] = ((checksum ushr 8) and 0xFF).toByte()
    bytes[7] = (checksum and 0xFF).toByte()

    return bytes.joinToString(separator = "") { byte ->
        byte.toInt().and(0xFF).toString(16).padStart(2, '0')
    }.uppercase().chunked(4).joinToString(separator = "-")
}

private fun parseJsonBootstrap(raw: String): ConnectionBootstrap {
    val payload = bootstrapJson.decodeFromString<ConnectionBootstrapPayload>(raw)
    require(payload.version == 1) { "Unsupported QR code version." }
    require(payload.host.isNotBlank()) { "Missing desktop address." }
    require(payload.port in 1..65535) { "Missing desktop port." }
    return ConnectionBootstrap(
        desktopId = payload.desktopId?.takeIf(String::isNotBlank),
        desktopName = payload.desktopName?.takeIf(String::isNotBlank) ?: "Desktop",
        host = payload.host.trim(),
        port = payload.port,
        source = ConnectionBootstrapSource.QrCode,
    )
}

private fun parseWebSocketBootstrap(raw: String): ConnectionBootstrap {
    val uri = URI(raw)
    require(uri.scheme.equals("ws", ignoreCase = true)) { "Unsupported connection scheme." }
    require(!uri.host.isNullOrBlank()) { "Missing desktop address." }
    require(uri.port in 1..65535) { "Missing desktop port." }
    return ConnectionBootstrap(
        desktopName = uri.host,
        host = uri.host,
        port = uri.port,
        source = ConnectionBootstrapSource.WebSocketUrl,
    )
}

private fun parseConnectionCode(raw: String): ConnectionBootstrap {
    val sanitized = raw
        .uppercase()
        .removePrefix("CR1-")
        .replace("-", "")
        .replace(" ", "")
    require(sanitized.length == 16) { "Connection code should be 16 hex characters." }
    val bytes = sanitized.chunked(2).map { chunk ->
        chunk.toInt(16).toByte()
    }
    val expectedChecksum = connectionCodeChecksum(bytes.toByteArray(), length = 6)
    val actualChecksum = ((bytes[6].toInt() and 0xFF) shl 8) or (bytes[7].toInt() and 0xFF)
    require(expectedChecksum == actualChecksum) { "Connection code is invalid." }

    val host = buildString {
        append(bytes[0].toInt() and 0xFF)
        append('.')
        append(bytes[1].toInt() and 0xFF)
        append('.')
        append(bytes[2].toInt() and 0xFF)
        append('.')
        append(bytes[3].toInt() and 0xFF)
    }
    val port = ((bytes[4].toInt() and 0xFF) shl 8) or (bytes[5].toInt() and 0xFF)
    return ConnectionBootstrap(
        desktopName = host,
        host = host,
        port = port,
        source = ConnectionBootstrapSource.ConnectionCode,
    )
}

private fun connectionCodeChecksum(
    bytes: ByteArray,
    length: Int,
): Int {
    var checksum = 0
    repeat(length) { index ->
        checksum = (checksum + (bytes[index].toInt() and 0xFF)) and 0xFFFF
    }
    return checksum
}
