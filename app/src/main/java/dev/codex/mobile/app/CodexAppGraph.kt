package dev.codex.mobile.app

import dev.codex.mobile.core.data.CodexRepository
import dev.codex.mobile.core.data.demo.DemoCodexRepository

object CodexAppGraph {
    val repository: CodexRepository by lazy { DemoCodexRepository() }
}
