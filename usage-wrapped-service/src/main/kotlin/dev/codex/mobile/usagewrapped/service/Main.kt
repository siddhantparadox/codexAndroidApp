package dev.codex.mobile.usagewrapped.service

import java.nio.file.Path
import java.util.concurrent.CountDownLatch

private const val DefaultListenHost: String = "0.0.0.0"
private const val DefaultPort: Int = 4501

private data class ServerConfig(
    val listenHost: String = DefaultListenHost,
    val port: Int = DefaultPort,
    val codexHome: Path = defaultCodexHome(),
) {
    val sessionRoot: Path
        get() = codexHome.resolve("sessions")
}

fun main(args: Array<String>) {
    val config: ServerConfig = parseArgs(args = args.toList())
    val server = UsageWrappedHttpServer().start(
        host = config.listenHost,
        port = config.port,
        sessionRoot = config.sessionRoot,
    )

    println("Codex usage wrapped service listening on http://${config.listenHost}:${config.port}")
    println("Reading session history from ${config.sessionRoot}")

    Runtime.getRuntime().addShutdownHook(
        Thread {
            server.stop(0)
        },
    )

    CountDownLatch(1).await()
}

private fun parseArgs(args: List<String>): ServerConfig {
    var config: ServerConfig = ServerConfig()
    var index = 0
    while (index < args.size) {
        when (args[index]) {
            "--listen" -> {
                config = config.copy(listenHost = args.getOrNull(index + 1) ?: config.listenHost)
                index += 2
            }

            "--port" -> {
                config = config.copy(
                    port = args.getOrNull(index + 1)?.toIntOrNull() ?: config.port,
                )
                index += 2
            }

            "--codex-home" -> {
                config = config.copy(
                    codexHome = args.getOrNull(index + 1)?.let(Path::of) ?: config.codexHome,
                )
                index += 2
            }

            else -> {
                index += 1
            }
        }
    }
    return config
}

private fun defaultCodexHome(): Path {
    val codexHomeOverride: String? = System.getenv("CODEX_HOME")
    return if (codexHomeOverride.isNullOrBlank()) {
        Path.of(System.getProperty("user.home"), ".codex")
    } else {
        Path.of(codexHomeOverride)
    }
}
