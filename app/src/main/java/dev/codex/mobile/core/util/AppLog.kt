package dev.codex.mobile.core.util

import android.util.Log

internal object AppLog {
    private const val TAG: String = "CodexMobile"

    internal fun screen(name: String): Unit {
        Log.d(TAG, "screen=$name")
    }

    internal fun action(name: String, detail: String? = null): Unit {
        if (detail.isNullOrBlank()) {
            Log.d(TAG, "action=$name")
        } else {
            Log.d(TAG, "action=$name detail=$detail")
        }
    }
}
