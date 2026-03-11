package dev.codex.mobile

import android.app.Application
import dev.codex.mobile.app.CodexAppGraph

class CodexMobileApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CodexAppGraph.initialize(this)
    }
}
