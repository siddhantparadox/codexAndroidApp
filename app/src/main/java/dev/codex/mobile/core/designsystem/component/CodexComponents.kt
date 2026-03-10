package dev.codex.mobile.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Approval
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.codex.mobile.core.designsystem.theme.cardBorder

@Composable
fun CodexCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.cardBorder),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (trailing != null) {
            Row(content = trailing)
        }
    }
}

@Composable
fun StatusChip(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    pulsingDot: Boolean = false,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (pulsingDot) {
            Canvas(modifier = Modifier.size(8.dp)) {
                drawCircle(color = color)
            }
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = color,
        )
    }
}

@Composable
fun InitialAvatar(
    initials: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
fun AvatarCluster(
    initials: List<String>,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
        initials.take(3).forEachIndexed { index, value ->
            InitialAvatar(
                initials = value,
                modifier = Modifier.padding(start = if (index == 0) 0.dp else (-8).dp),
            )
        }
    }
}

enum class TopLevelDestination(
    val label: String,
    val icon: ImageVector,
) {
    Dashboard("Home", Icons.Rounded.Home),
    Threads("Threads", Icons.AutoMirrored.Rounded.ViewList),
    Approvals("Queue", Icons.Rounded.Approval),
    Settings("Settings", Icons.Rounded.Settings),
}

@Composable
fun CodexBottomBar(
    currentDestination: TopLevelDestination,
    pendingApprovals: Int,
    onDestinationSelected: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.cardBorder),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            TopLevelDestination.entries.forEach { destination ->
                val selected = currentDestination == destination
                val tint by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "navTint",
                )
                val interactionSource = remember { MutableInteractionSource() }

                Column(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                        ) { onDestinationSelected(destination) }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                                    .padding(horizontal = 18.dp, vertical = 6.dp),
                            ) {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = destination.label,
                                    tint = tint,
                                )
                            }
                        } else {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.label,
                                tint = tint,
                            )
                        }
                        if (destination == TopLevelDestination.Approvals && pendingApprovals > 0) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ) {
                                Text(
                                    text = pendingApprovals.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = destination.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = tint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
