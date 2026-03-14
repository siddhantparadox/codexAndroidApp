package dev.codex.mobile.core.data.usagewrapped

import kotlinx.serialization.json.Json

internal val usageWrappedJson: Json = Json {
    ignoreUnknownKeys = true
}
