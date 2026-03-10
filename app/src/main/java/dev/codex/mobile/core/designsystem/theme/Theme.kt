package dev.codex.mobile.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.luminance

private val LightColorScheme = lightColorScheme(
    primary = DeepTeal,
    onPrimary = PaperWhite,
    primaryContainer = DeepTeal.copy(alpha = 0.12f),
    onPrimaryContainer = DeepTeal,
    background = Ivory,
    onBackground = Charcoal,
    surface = PaperWhite,
    onSurface = Charcoal,
    surfaceVariant = Frost,
    onSurfaceVariant = Stone,
    outline = androidx.compose.ui.graphics.Color(0xFFD7DFE5),
    error = Brick,
)

private val DarkColorScheme = darkColorScheme(
    primary = DeepTealDark,
    onPrimary = Graphite,
    primaryContainer = DeepTealDark.copy(alpha = 0.16f),
    onPrimaryContainer = DeepTealDark,
    background = Graphite,
    onBackground = PaperWhite,
    surface = SlateSurface,
    onSurface = PaperWhite,
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF202B30),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFB3C0C7),
    outline = SlateMuted,
    error = Brick,
)

private val CodexShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

val ColorScheme.cardBorder: androidx.compose.ui.graphics.Color
    @Composable
    get() = outline.copy(alpha = if (background.luminance() > 0.5f) 0.65f else 0.8f)

@Composable
fun CodexMobileTheme(
    useDarkTheme: Boolean?,
    content: @Composable () -> Unit,
) {
    val darkTheme = useDarkTheme ?: isSystemInDarkTheme()

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = CodexTypography,
        shapes = CodexShapes,
        content = content,
    )
}
