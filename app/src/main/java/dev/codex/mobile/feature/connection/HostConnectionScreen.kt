package dev.codex.mobile.feature.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LaptopMac
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import dev.codex.mobile.core.designsystem.component.StatusChip
import dev.codex.mobile.core.model.HostKind

@Composable
fun HostConnectionScreen(
    onNavigateBack: () -> Unit,
    viewModel: HostConnectionViewModel = viewModel(
        factory = HostConnectionViewModel.factory(CodexAppGraph.repository),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                    Text(
                        text = "Host Connection",
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        item {
            Text(
                text = "Add New Connection".uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            CodexCard {
                OutlinedTextField(
                    value = uiState.hostName,
                    onValueChange = viewModel::onHostNameChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Host Name") },
                    placeholder = { Text("e.g. Remote Server") },
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = uiState.address,
                        onValueChange = viewModel::onAddressChanged,
                        modifier = Modifier.weight(1f),
                        label = { Text("IP Address") },
                        placeholder = { Text("192.168.1.15") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = uiState.port,
                        onValueChange = viewModel::onPortChanged,
                        modifier = Modifier.weight(0.6f),
                        label = { Text("Port") },
                        singleLine = true,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                        .clickable(onClick = viewModel::saveConnection)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "Save Connection",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Saved Connections".uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StatusChip(
                    label = "${uiState.hosts.count { it.isActive }} Active",
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        items(uiState.hosts, key = { host -> host.id }) { host ->
            CodexCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.activateHost(host.id) },
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
                                .size(46.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), MaterialTheme.shapes.small),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = if (host.kind == HostKind.Laptop) Icons.Rounded.LaptopMac else Icons.Rounded.Computer,
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
                    if (host.isActive) {
                        StatusChip(
                            label = "Active",
                            color = MaterialTheme.colorScheme.primary,
                            pulsingDot = true,
                        )
                    }
                }
            }
        }
        item {
            CodexCard {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    Text(
                        text = "Use a trusted LAN address for codex app-server and keep the host bound to a private endpoint. The mobile client is a control surface for the laptop runtime, not a separate execution environment.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
