package dev.codex.mobile.usagewrapped.service

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class UsageWrappedHttpServer(
    private val aggregator: UsageWrappedAggregator = UsageWrappedAggregator(),
    private val json: Json = Json {
        encodeDefaults = true
        prettyPrint = true
    },
) {
    fun start(
        host: String,
        port: Int,
        sessionRoot: Path,
    ): HttpServer {
        val server: HttpServer = HttpServer.create(InetSocketAddress(host, port), 0)
        server.createContext("/healthz") { exchange ->
            exchange.respondJson(
                statusCode = 200,
                body = """{"ok":true}""",
            )
        }
        server.createContext("/usage-wrapped") { exchange ->
            if (exchange.requestMethod != "GET") {
                exchange.respondJson(
                    statusCode = 405,
                    body = """{"error":"Method not allowed"}""",
                )
                return@createContext
            }

            runCatching {
                json.encodeToString(aggregator.summarize(sessionRoot = sessionRoot))
            }.onSuccess { body ->
                exchange.respondJson(statusCode = 200, body = body)
            }.onFailure { error ->
                val message: String = error.message ?: "Unable to build usage summary."
                exchange.respondJson(
                    statusCode = 500,
                    body = """{"error":${json.encodeToString(message)}}""",
                )
            }
        }
        server.start()
        return server
    }
}

private fun HttpExchange.respondJson(
    statusCode: Int,
    body: String,
): Unit {
    val bytes: ByteArray = body.toByteArray(StandardCharsets.UTF_8)
    responseHeaders.add("Content-Type", "application/json; charset=utf-8")
    sendResponseHeaders(statusCode, bytes.size.toLong())
    responseBody.use { output ->
        output.write(bytes)
    }
    close()
}
