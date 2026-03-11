package dev.codex.mobile.app

import android.content.Context
import dev.codex.mobile.core.data.CodexRepository
import dev.codex.mobile.core.data.appserver.AppServerCodexRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object CodexAppGraph {
    private lateinit var appContext: Context
    private val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    val repository: CodexRepository by lazy {
        AppServerCodexRepository(
            context = appContext,
            applicationScope = applicationScope,
        )
    }
}
