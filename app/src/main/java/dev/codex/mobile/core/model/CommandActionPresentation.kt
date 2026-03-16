package dev.codex.mobile.core.model

internal fun CommandActionHint.displayLabel(): String = buildString {
    append(type)
    name?.let { append(" • $it") }
    path?.let { append(" • $it") }
    query?.let { append(" • $it") }
}
