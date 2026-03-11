package dev.codex.mobile.feature.threaddetail

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.codex.mobile.core.designsystem.component.CodexCard
import dev.codex.mobile.core.designsystem.component.StatusChip
import dev.codex.mobile.core.model.ThreadItemStatus

internal enum class TechnicalCardFamily {
    Plan,
    Reasoning,
    Command,
    Patch,
    Mcp,
    Tool,
    Web,
    Collab,
    Review,
    Image,
    System,
}

private data class TechnicalCardPalette(
    val accent: Color,
    val border: Color,
    val container: Color,
)

@Composable
internal fun TechnicalCard(
    rememberKey: String,
    title: String,
    badge: String,
    preview: String?,
    icon: ImageVector,
    family: TechnicalCardFamily,
    modifier: Modifier = Modifier,
    status: ThreadItemStatus? = null,
    statusLabel: String? = null,
    defaultExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = technicalCardPalette(family)
    var expanded by remember(rememberKey, defaultExpanded) { mutableStateOf(defaultExpanded) }

    CodexCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(0.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = palette.container,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(width = 1.dp, color = palette.border),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = palette.accent.copy(alpha = 0.14f),
                                    shape = CircleShape,
                                )
                                .padding(8.dp),
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = palette.accent,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    color = palette.accent.copy(alpha = 0.14f),
                                    shape = CircleShape,
                                ) {
                                    Text(
                                        text = badge,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = palette.accent,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                                when {
                                    status != null -> StatusChip(
                                        label = technicalStatusLabel(status),
                                        color = technicalStatusColor(status),
                                    )

                                    statusLabel != null -> StatusChip(
                                        label = statusLabel,
                                        color = palette.accent,
                                    )
                                }
                            }
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = if (expanded) 4 else 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            preview
                                ?.trim()
                                ?.takeIf { it.isNotBlank() }
                                ?.let { text ->
                                    Text(
                                        text = text,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = if (expanded) 4 else 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Icon(
                            imageVector = if (expanded) {
                                Icons.Rounded.KeyboardArrowDown
                            } else {
                                Icons.AutoMirrored.Rounded.KeyboardArrowRight
                            },
                            contentDescription = null,
                            tint = palette.accent,
                        )
                    }
                }

                if (expanded) {
                    HorizontalDivider(color = palette.border)
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        content = content,
                    )
                }
            }
        }
    }
}

@Composable
private fun technicalCardPalette(family: TechnicalCardFamily): TechnicalCardPalette = when (family) {
    TechnicalCardFamily.Plan -> paletteFrom(Color(0xFF2B6FE8))
    TechnicalCardFamily.Reasoning -> paletteFrom(Color(0xFF8F6A20))
    TechnicalCardFamily.Command -> paletteFrom(Color(0xFF4B6375))
    TechnicalCardFamily.Patch -> paletteFrom(Color(0xFF2F9A58))
    TechnicalCardFamily.Mcp -> paletteFrom(Color(0xFF0F8B8D))
    TechnicalCardFamily.Tool -> paletteFrom(Color(0xFF5364E7))
    TechnicalCardFamily.Web -> paletteFrom(Color(0xFFD59734))
    TechnicalCardFamily.Collab -> paletteFrom(Color(0xFF1D9CB6))
    TechnicalCardFamily.Review -> paletteFrom(Color(0xFFCC6F2C))
    TechnicalCardFamily.Image -> paletteFrom(MaterialTheme.colorScheme.secondary)
    TechnicalCardFamily.System -> paletteFrom(MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun paletteFrom(accent: Color): TechnicalCardPalette = TechnicalCardPalette(
    accent = accent,
    border = accent.copy(alpha = 0.24f),
    container = accent.copy(alpha = 0.07f),
)

@Composable
private fun technicalStatusColor(status: ThreadItemStatus): Color = when (status) {
    ThreadItemStatus.InProgress -> MaterialTheme.colorScheme.primary
    ThreadItemStatus.Completed -> Color(0xFF2F9A58)
    ThreadItemStatus.Failed -> MaterialTheme.colorScheme.error
    ThreadItemStatus.Declined -> Color(0xFFD59734)
}

private fun technicalStatusLabel(status: ThreadItemStatus): String = when (status) {
    ThreadItemStatus.InProgress -> "In Progress"
    ThreadItemStatus.Completed -> "Completed"
    ThreadItemStatus.Failed -> "Failed"
    ThreadItemStatus.Declined -> "Declined"
}
