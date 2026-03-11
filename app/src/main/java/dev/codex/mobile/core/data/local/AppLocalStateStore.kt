package dev.codex.mobile.core.data.local

import android.content.Context
import dev.codex.mobile.core.model.AppPreferences
import dev.codex.mobile.core.model.HostProfile
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class PersistedAppState(
    val preferences: AppPreferences = AppPreferences(),
    val hosts: List<HostProfile> = emptyList(),
)

internal class AppLocalStateStore(
    context: Context,
    private val json: Json,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val file: File = File(context.filesDir, "codex-mobile-state.json")

    suspend fun load(): PersistedAppState = withContext(ioDispatcher) {
        if (!file.exists()) {
            PersistedAppState()
        } else {
            runCatching {
                json.decodeFromString<PersistedAppState>(file.readText())
            }.getOrDefault(PersistedAppState())
        }
    }

    suspend fun save(state: PersistedAppState): Unit = withContext(ioDispatcher) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(state))
    }
}
