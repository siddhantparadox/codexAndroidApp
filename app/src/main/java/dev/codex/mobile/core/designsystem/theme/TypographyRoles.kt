package dev.codex.mobile.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle

val Typography.screenTitle: TextStyle
    get() = headlineMedium

val Typography.panelHeadline: TextStyle
    get() = headlineSmall

val Typography.cardTitle: TextStyle
    get() = titleMedium

val Typography.listItemTitle: TextStyle
    get() = titleMedium

val Typography.bodyText: TextStyle
    get() = bodyLarge

val Typography.supportingText: TextStyle
    get() = bodyMedium

val Typography.denseSupportingText: TextStyle
    get() = bodySmall

val Typography.sectionLabel: TextStyle
    get() = labelLarge

val Typography.statusText: TextStyle
    get() = labelLarge

val Typography.metaText: TextStyle
    get() = labelSmall

val Typography.codeInline: TextStyle
    get() = labelMedium.copy(fontFamily = CodexMono)

val Typography.codeBlock: TextStyle
    get() = bodySmall.copy(fontFamily = CodexMono)
