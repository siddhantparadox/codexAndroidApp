package dev.codex.mobile.feature.connection

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LaptopMac
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import dev.codex.mobile.app.CodexAppGraph
import dev.codex.mobile.core.designsystem.component.CodexCard
import dev.codex.mobile.core.designsystem.component.StatusChip
import dev.codex.mobile.core.designsystem.theme.CodexSpacing
import dev.codex.mobile.core.model.AccountState
import dev.codex.mobile.core.model.ConnectionPhase
import dev.codex.mobile.core.model.HostKind
import dev.codex.mobile.core.model.summary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostConnectionScreen(
    onNavigateBack: () -> Unit,
    viewModel: HostConnectionViewModel = viewModel(
        factory = HostConnectionViewModel.factory(CodexAppGraph.repository),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val qrScanner = rememberDesktopQrScanner(context = context)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = CodexSpacing.screenHorizontal,
            top = CodexSpacing.screenTop,
            end = CodexSpacing.screenHorizontal,
            bottom = CodexSpacing.screenBottom,
        ),
        verticalArrangement = Arrangement.spacedBy(CodexSpacing.sectionGap),
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
            ConnectionStatusCard(
                connectionPhase = uiState.connection.phase,
                connectionMessage = uiState.connection.message,
                account = uiState.account,
            )
        }
        item {
            ConnectDesktopCard(
                uiState = uiState,
                onScanQr = {
                    val scanner = qrScanner
                    if (scanner == null) {
                        viewModel.showBootstrapError("QR scanning is unavailable on this device.")
                    } else {
                        scanner.startScan()
                            .addOnSuccessListener { result ->
                                val rawValue = result.rawValue.orEmpty()
                                if (rawValue.isBlank()) {
                                    viewModel.showBootstrapError("The scanned QR code did not contain a connection payload.")
                                } else {
                                    viewModel.handleScannedBootstrap(rawValue)
                                }
                            }
                            .addOnFailureListener { error ->
                                if (error is ApiException && error.statusCode == CommonStatusCodes.CANCELED) {
                                    return@addOnFailureListener
                                }
                                viewModel.showBootstrapError(error.message ?: "Unable to scan the desktop QR code.")
                            }
                    }
                },
                onConnectionCodeChanged = viewModel::onConnectionCodeChanged,
                onSubmitConnectionCode = viewModel::submitConnectionCode,
                onToggleManualEntry = viewModel::toggleManualEntry,
                onHostNameChanged = viewModel::onHostNameChanged,
                onAddressChanged = viewModel::onAddressChanged,
                onPortChanged = viewModel::onPortChanged,
                onSaveManualConnection = viewModel::saveConnection,
            )
        }
        if (uiState.hosts.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Remembered Desktops".uppercase(),
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
                RememberedDesktopCard(
                    host = host,
                    activeHostId = uiState.connection.activeHostId,
                    connectionPhase = uiState.connection.phase,
                    onClick = { viewModel.activateHost(host.id) },
                )
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
                    Spacer(modifier = Modifier.size(CodexSpacing.sectionGap))
                    Text(
                        text = "Use a trusted LAN address for codex app-server and keep the host bound to a private endpoint. The mobile client is a control surface for the desktop runtime, not a separate execution environment.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    uiState.pendingBootstrap?.let { bootstrap ->
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissPendingBootstrap,
        ) {
            PendingBootstrapSheet(
                bootstrap = bootstrap,
                onConnect = viewModel::confirmPendingBootstrap,
                onCancel = viewModel::dismissPendingBootstrap,
            )
        }
    }
}

@Composable
private fun ConnectDesktopCard(
    uiState: HostConnectionUiState,
    onScanQr: () -> Unit,
    onConnectionCodeChanged: (String) -> Unit,
    onSubmitConnectionCode: () -> Unit,
    onToggleManualEntry: () -> Unit,
    onHostNameChanged: (String) -> Unit,
    onAddressChanged: (String) -> Unit,
    onPortChanged: (String) -> Unit,
    onSaveManualConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CodexCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Connect Your Desktop",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(CodexSpacing.microGap))
        Text(
            text = "Run npx codexremote on your computer, then scan the QR code it shows.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(CodexSpacing.sectionGap))
        Button(
            onClick = onScanQr,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Scan QR Code")
        }
        Spacer(modifier = Modifier.height(CodexSpacing.listGap))
        Text(
            text = "Type a connection code instead",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(CodexSpacing.microGap))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CodexSpacing.listGap),
            verticalAlignment = Alignment.Top,
        ) {
            OutlinedTextField(
                value = uiState.connectionCode,
                onValueChange = onConnectionCodeChanged,
                modifier = Modifier.weight(1f),
                label = { Text("Connection Code") },
                placeholder = { Text("ABCD-EF12-3456-7890") },
                singleLine = true,
            )
            OutlinedButton(
                onClick = onSubmitConnectionCode,
                modifier = Modifier.align(Alignment.CenterVertically),
            ) {
                Text("Use Code")
            }
        }
        uiState.bootstrapError?.let { error ->
            Spacer(modifier = Modifier.height(CodexSpacing.microGap))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(modifier = Modifier.height(CodexSpacing.listGap))
        TextButton(onClick = onToggleManualEntry) {
            Text(if (uiState.showManualEntry) "Hide Advanced Manual Entry" else "Advanced Manual Entry")
        }
        if (uiState.showManualEntry) {
            Spacer(modifier = Modifier.height(CodexSpacing.listGap))
            OutlinedTextField(
                value = uiState.hostName,
                onValueChange = onHostNameChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Desktop Name") },
                placeholder = { Text("Defaults to the address if left blank") },
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(CodexSpacing.listGap))
            Row(horizontalArrangement = Arrangement.spacedBy(CodexSpacing.sectionGap)) {
                OutlinedTextField(
                    value = uiState.address,
                    onValueChange = onAddressChanged,
                    modifier = Modifier.weight(1f),
                    label = { Text("IP Address") },
                    placeholder = { Text("192.168.1.15") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = uiState.port,
                    onValueChange = onPortChanged,
                    modifier = Modifier.weight(0.6f),
                    label = { Text("Port") },
                    singleLine = true,
                )
            }
            Spacer(modifier = Modifier.height(CodexSpacing.listGap))
            OutlinedButton(
                onClick = onSaveManualConnection,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save And Connect")
            }
        }
    }
}

@Composable
private fun RememberedDesktopCard(
    host: dev.codex.mobile.core.model.HostProfile,
    activeHostId: String?,
    connectionPhase: ConnectionPhase,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CodexCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                    modifier = Modifier.size(46.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (host.kind == HostKind.Laptop) Icons.Rounded.LaptopMac else Icons.Rounded.Computer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(modifier = Modifier.size(CodexSpacing.sectionGap))
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
            Spacer(modifier = Modifier.size(CodexSpacing.sectionGap))
            StatusChip(
                label = hostStatusLabel(
                    hostId = host.id,
                    activeHostId = activeHostId,
                    connectionPhase = connectionPhase,
                ),
                color = hostStatusColor(
                    hostId = host.id,
                    activeHostId = activeHostId,
                    connectionPhase = connectionPhase,
                ),
                pulsingDot = host.id == activeHostId && connectionPhase == ConnectionPhase.Connected,
            )
        }
    }
}

@Composable
private fun PendingBootstrapSheet(
    bootstrap: ConnectionBootstrap,
    onConnect: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = CodexSpacing.screenHorizontal,
                end = CodexSpacing.screenHorizontal,
                bottom = CodexSpacing.screenBottom,
            ),
        verticalArrangement = Arrangement.spacedBy(CodexSpacing.sectionGap),
    ) {
        Text(
            text = "Connect To Desktop",
            style = MaterialTheme.typography.headlineSmall,
        )
        CodexCard {
            Text(
                text = bootstrap.desktopName,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(CodexSpacing.microGap))
            Text(
                text = "${bootstrap.host}:${bootstrap.port}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(CodexSpacing.listGap))
            Text(
                text = "Trusted local network only. This phone will control the desktop runtime over your LAN.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(
            onClick = onConnect,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Connect")
        }
        TextButton(
            onClick = onCancel,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text("Cancel")
        }
    }
}

@Composable
private fun ConnectionStatusCard(
    connectionPhase: ConnectionPhase,
    connectionMessage: String?,
    account: AccountState,
) {
    CodexCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (connectionPhase == ConnectionPhase.Connected) {
                        Icons.Rounded.LaptopMac
                    } else {
                        Icons.Rounded.LinkOff
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.size(12.dp))
                Column {
                    Text(
                        text = "Desktop Runtime",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = account.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.size(12.dp))
            StatusChip(
                label = hostConnectionLabel(connectionPhase),
                color = hostConnectionColor(connectionPhase),
                pulsingDot = connectionPhase == ConnectionPhase.Connected,
            )
        }
        connectionMessage?.let { message ->
            Spacer(modifier = Modifier.height(CodexSpacing.listGap))
            Text(
                text = message,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun hostStatusLabel(
    hostId: String,
    activeHostId: String?,
    connectionPhase: ConnectionPhase,
): String = if (hostId != activeHostId) {
    "Saved"
} else {
    hostConnectionLabel(connectionPhase)
}

@Composable
private fun hostStatusColor(
    hostId: String,
    activeHostId: String?,
    connectionPhase: ConnectionPhase,
): Color = if (hostId != activeHostId) {
    MaterialTheme.colorScheme.onSurfaceVariant
} else {
    hostConnectionColor(connectionPhase)
}

private fun hostConnectionLabel(connectionPhase: ConnectionPhase): String = when (connectionPhase) {
    ConnectionPhase.Connected -> "Connected"
    ConnectionPhase.Connecting -> "Connecting"
    ConnectionPhase.Disconnected -> "Offline"
    ConnectionPhase.Error -> "Error"
    ConnectionPhase.Idle -> "Idle"
}

@Composable
private fun hostConnectionColor(connectionPhase: ConnectionPhase): Color = when (connectionPhase) {
    ConnectionPhase.Connected -> MaterialTheme.colorScheme.primary
    ConnectionPhase.Connecting -> Color(0xFFD59734)
    ConnectionPhase.Disconnected -> MaterialTheme.colorScheme.onSurfaceVariant
    ConnectionPhase.Error -> MaterialTheme.colorScheme.error
    ConnectionPhase.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun rememberDesktopQrScanner(
    context: Context,
): GmsBarcodeScanner? {
    val activity = remember(context) { context.findActivity() }
    return remember(activity) {
        activity?.let {
            val options = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .enableAutoZoom()
                .build()
            GmsBarcodeScanning.getClient(it, options)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
