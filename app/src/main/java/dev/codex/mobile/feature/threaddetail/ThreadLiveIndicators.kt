package dev.codex.mobile.feature.threaddetail

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.codex.mobile.core.designsystem.theme.CodexSpacing
import dev.codex.mobile.core.designsystem.theme.codeInline

@Composable
internal fun LiveStatusBadge(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.12f),
        shape = CircleShape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(CodexSpacing.tightGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.codeInline,
                color = color,
            )
            LiveDots(color = color)
        }
    }
}

@Composable
internal fun LivePulseDot(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 7.dp,
) {
    val transition = rememberInfiniteTransition(label = "live_pulse_dot")
    val alpha by transition.animateFloat(
        initialValue = 0.52f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "live_pulse_dot_alpha",
    )
    val scale by transition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "live_pulse_dot_scale",
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .background(color = color, shape = CircleShape),
    )
}

@Composable
internal fun LiveAccentLine(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "live_accent_line")
    val offsetProgress by transition.animateFloat(
        initialValue = -0.45f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "live_accent_line_offset",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp)
            .drawWithCache {
                val width = size.width
                val highlightStartX = width * offsetProgress - width * 0.35f
                val highlightEndX = highlightStartX + width * 0.55f
                onDrawBehind {
                    drawRoundRect(
                        color = color.copy(alpha = 0.14f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(999f, 999f),
                    )
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                color.copy(alpha = 0f),
                                color.copy(alpha = 0.42f),
                                color.copy(alpha = 0f),
                            ),
                            startX = highlightStartX,
                            endX = highlightEndX,
                        ),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(999f, 999f),
                    )
                }
            },
    )
}

@Composable
internal fun liveContainerColor(
    accent: Color,
    selected: Boolean,
): Color {
    val transition = rememberInfiniteTransition(label = "live_container_color")
    val animatedAlpha by transition.animateFloat(
        initialValue = if (selected) 0.16f else 0.1f,
        targetValue = if (selected) 0.24f else 0.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "live_container_color_alpha",
    )
    return accent.copy(alpha = animatedAlpha)
}

@Composable
private fun LiveDots(
    color: Color,
) {
    val transition = rememberInfiniteTransition(label = "live_dots")
    val dotOneAlpha by transition.animateFloat(
        initialValue = 0.28f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing, delayMillis = 0),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "live_dot_1_alpha",
    )
    val dotTwoAlpha by transition.animateFloat(
        initialValue = 0.28f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing, delayMillis = 150),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "live_dot_2_alpha",
    )
    val dotThreeAlpha by transition.animateFloat(
        initialValue = 0.28f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing, delayMillis = 300),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "live_dot_3_alpha",
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(CodexSpacing.microGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiveDot(color = color, alpha = dotOneAlpha)
        LiveDot(color = color, alpha = dotTwoAlpha)
        LiveDot(color = color, alpha = dotThreeAlpha)
    }
}

@Composable
private fun LiveDot(
    color: Color,
    alpha: Float,
) {
    Box(
        modifier = Modifier
            .size(4.dp)
            .graphicsLayer { this.alpha = alpha }
            .background(color = color, shape = RoundedCornerShape(999.dp)),
    )
}

