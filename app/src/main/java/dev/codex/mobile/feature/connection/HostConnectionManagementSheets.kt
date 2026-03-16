package dev.codex.mobile.feature.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.codex.mobile.core.designsystem.component.CodexCard
import dev.codex.mobile.core.designsystem.theme.CodexSpacing
import dev.codex.mobile.core.designsystem.theme.cardTitle
import dev.codex.mobile.core.designsystem.theme.panelHeadline
import dev.codex.mobile.core.designsystem.theme.supportingText
import dev.codex.mobile.core.model.HostProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RememberedDesktopActionSheet(
    host: HostProfile,
    onConnect: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = CodexSpacing.screenHorizontal,
                    end = CodexSpacing.screenHorizontal,
                    bottom = CodexSpacing.screenBottom,
                ),
            verticalArrangement = Arrangement.spacedBy(CodexSpacing.sectionGap),
        ) {
            Text(
                text = "Connection actions",
                style = MaterialTheme.typography.panelHeadline,
            )
            HostSummaryCard(host = host)
            Button(
                onClick = onConnect,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Connect")
            }
            OutlinedButton(
                onClick = onRename,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Rename connection")
            }
            TextButton(
                onClick = onRemove,
                modifier = Modifier.align(Alignment.End),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text("Remove connection")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RenameConnectionSheet(
    host: HostProfile,
    renameValue: String,
    onRenameValueChanged: (String) -> Unit,
    onSave: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val normalizedRenameValue = renameValue.trim()
    val saveEnabled = normalizedRenameValue.isNotEmpty() && normalizedRenameValue != host.name

    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = CodexSpacing.screenHorizontal,
                    end = CodexSpacing.screenHorizontal,
                    bottom = CodexSpacing.screenBottom,
                ),
            verticalArrangement = Arrangement.spacedBy(CodexSpacing.sectionGap),
        ) {
            Text(
                text = "Rename connection",
                style = MaterialTheme.typography.panelHeadline,
            )
            HostSummaryCard(host = host)
            OutlinedTextField(
                value = renameValue,
                onValueChange = onRenameValueChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Connection name") },
                singleLine = true,
            )
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
                enabled = saveEnabled,
            ) {
                Text("Save")
            }
            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Cancel")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RemoveConnectionSheet(
    host: HostProfile,
    isActiveConnection: Boolean,
    isOnlyConnection: Boolean,
    onRemove: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = CodexSpacing.screenHorizontal,
                    end = CodexSpacing.screenHorizontal,
                    bottom = CodexSpacing.screenBottom,
                ),
            verticalArrangement = Arrangement.spacedBy(CodexSpacing.sectionGap),
        ) {
            Text(
                text = "Remove connection",
                style = MaterialTheme.typography.panelHeadline,
            )
            HostSummaryCard(host = host)
            Text(
                text = removeConnectionMessage(
                    hostName = host.name,
                    isActiveConnection = isActiveConnection,
                    isOnlyConnection = isOnlyConnection,
                ),
                style = MaterialTheme.typography.supportingText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onRemove,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text("Remove connection")
            }
            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Cancel")
            }
        }
    }
}

@Composable
internal fun RememberedDesktopsEmptyStateCard(
    modifier: Modifier = Modifier,
) {
    CodexCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "No saved desktops",
            style = MaterialTheme.typography.cardTitle,
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(CodexSpacing.microGap))
        Text(
            text = "Scan a QR code or use a connection code to add a desktop connection.",
            style = MaterialTheme.typography.supportingText,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HostSummaryCard(
    host: HostProfile,
) {
    CodexCard {
        Text(
            text = host.name,
            style = MaterialTheme.typography.cardTitle,
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(CodexSpacing.microGap))
        Text(
            text = "${host.address}:${host.port}",
            style = MaterialTheme.typography.supportingText,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun removeConnectionMessage(
    hostName: String,
    isActiveConnection: Boolean,
    isOnlyConnection: Boolean,
): String = when {
    isActiveConnection && isOnlyConnection ->
        "Removing this connection will disconnect this phone from $hostName and leave you with no saved desktops."

    isActiveConnection ->
        "Removing this connection will disconnect this phone from $hostName."

    isOnlyConnection ->
        "This is your last saved desktop connection. You can pair again later with a QR code or connection code."

    else ->
        "This removes the saved connection from this phone. You can pair again later with a QR code or connection code."
}
