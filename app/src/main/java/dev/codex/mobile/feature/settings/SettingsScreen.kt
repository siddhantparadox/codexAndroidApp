package dev.codex.mobile.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.codex.mobile.app.CodexAppGraph
import dev.codex.mobile.core.designsystem.component.CodexCard
import dev.codex.mobile.core.model.ThemePreference

@Composable
fun SettingsScreen(
    onOpenHostConnection: () -> Unit,
    viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(CodexAppGraph.repository),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
            )
        }
        item { SectionLabel(title = "Saved Connections") }
        items(uiState.hosts, key = { host -> host.id }) { host ->
            CodexCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenHostConnection),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), MaterialTheme.shapes.small),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Computer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(modifier = Modifier.size(12.dp))
                        Column {
                            Text(
                                text = host.name,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${host.address}:${host.port}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.size(12.dp))
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                    .clickable(onClick = onOpenHostConnection)
                    .padding(18.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Host Connection Manager",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "Add, activate, and test laptop endpoints",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(modifier = Modifier.size(12.dp))
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        item { SectionLabel(title = "Preferences") }
        item {
            PreferenceCard(
                title = "Theme Mode",
                subtitle = "Switch between light, dark, or system styling",
                icon = Icons.Rounded.DarkMode,
            ) {
                ThemePreference.entries.forEach { option ->
                    val selected = option == uiState.preferences.themePreference
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                shape = CircleShape,
                            )
                            .clickable { viewModel.setThemePreference(option) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = option.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            TogglePreference(
                title = "Connection Alerts",
                subtitle = "Notify when a host disconnects or requests approval",
                icon = Icons.Rounded.NotificationsActive,
                checked = uiState.preferences.connectionAlerts,
                onCheckedChange = viewModel::setConnectionAlerts,
            )
        }
        item {
            TogglePreference(
                title = "Secure Shell (SSH)",
                subtitle = "Prepare the demo for future secure transport",
                icon = Icons.Rounded.Security,
                checked = uiState.preferences.secureShellEnabled,
                onCheckedChange = viewModel::setSecureShell,
            )
        }
        item {
            CodexCard {
                Text(
                    text = "Security note",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "All connections are limited to your trusted local network. Codex Mobile does not transmit host credentials to external services.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PreferenceCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit,
) {
    CodexCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.size(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun TogglePreference(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    CodexCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.size(12.dp))
                Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}
