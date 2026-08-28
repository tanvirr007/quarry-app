package app.quarry.tanvir.info.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.quarry.tanvir.info.data.preferences.ThemeMode
import app.quarry.tanvir.info.ui.onboarding.OnboardingDialog

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Preferences",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp)
        )

        // 1. Appearance & Theme
        SettingsActionItem(
            icon = Icons.Rounded.DarkMode,
            title = "Appearance & Theme",
            subtitle = uiState.themeMode.displayName,
            onClick = { viewModel.showThemeDialog() }
        )

        // 2. Security & Biometrics
        SettingsSwitchItem(
            icon = Icons.Rounded.Fingerprint,
            title = "Biometric & PIN Protection",
            subtitle = "Require authentication for file deletion and rename",
            checked = uiState.isBiometricEnabled,
            onCheckedChange = { viewModel.setBiometricEnabled(it) }
        )

        // 3. Scan & Exclusions
        SettingsActionItem(
            icon = Icons.Rounded.Tune,
            title = "Scan & Exclusions",
            subtitle = "${uiState.excludedFolders.size} custom excluded paths",
            onClick = { viewModel.showExclusionsDialog() }
        )

        // 4. Storage Volumes
        SettingsActionItem(
            icon = Icons.Rounded.SdStorage,
            title = "Storage Volumes",
            subtitle = "${uiState.detectedVolumes.size} drives detected (Internal, SD, USB)",
            onClick = { viewModel.showVolumesDialog() }
        )

        // 5. Historical Snapshots & Compare
        SettingsActionItem(
            icon = Icons.Rounded.History,
            title = "Storage Snapshots & Comparison",
            subtitle = "${uiState.snapshots.size} historical snapshots saved",
            onClick = { viewModel.showComparisonDialog() }
        )

        // 6. Export Storage Report
        SettingsActionItem(
            icon = Icons.Rounded.Share,
            title = "Export Storage Report",
            subtitle = "Generate & share local diagnostic storage summary",
            onClick = { viewModel.exportReport(context) }
        )

        Text(
            text = "About & Help",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp)
        )

        // 7. Onboarding Guide
        SettingsActionItem(
            icon = Icons.Rounded.HelpOutline,
            title = "Quarry Walkthrough",
            subtitle = "Revisit the 4-step feature tour & privacy guide",
            onClick = { viewModel.showOnboarding() }
        )

        // 8. About Quarry
        SettingsActionItem(
            icon = Icons.Rounded.Info,
            title = "Quarry v1.0.0",
            subtitle = "See where your storage goes. 100% Offline & Private.",
            onClick = {}
        )
    }

    // Theme Selection Dialog
    if (uiState.isThemeDialogVisible) {
        ThemeSelectionDialog(
            currentTheme = uiState.themeMode,
            onSelectTheme = { viewModel.setThemeMode(it) },
            onDismiss = { viewModel.hideThemeDialog() }
        )
    }

    // Storage Volumes Dialog
    if (uiState.isVolumesDialogVisible) {
        StorageVolumesDialog(
            volumes = uiState.detectedVolumes,
            onDismiss = { viewModel.hideVolumesDialog() }
        )
    }

    // Scan Comparison Dialog
    if (uiState.isComparisonDialogVisible) {
        ScanComparisonDialog(
            snapshots = uiState.snapshots,
            onDismiss = { viewModel.hideComparisonDialog() }
        )
    }

    // Exclusions Dialog
    if (uiState.isExclusionsDialogVisible) {
        ExclusionsDialog(
            excludedFolders = uiState.excludedFolders,
            onAddExclusion = { viewModel.addExclusion(it) },
            onRemoveExclusion = { viewModel.removeExclusion(it) },
            onDismiss = { viewModel.hideExclusionsDialog() }
        )
    }

    // Onboarding Dialog
    if (uiState.isOnboardingVisible) {
        OnboardingDialog(
            onDismiss = { viewModel.hideOnboarding() },
            onCompleted = { viewModel.hideOnboarding() }
        )
    }
}

@Composable
private fun ThemeSelectionDialog(
    currentTheme: ThemeMode,
    onSelectTheme: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Theme") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (mode == currentTheme),
                                onClick = { onSelectTheme(mode) }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RadioButton(
                            selected = (mode == currentTheme),
                            onClick = { onSelectTheme(mode) }
                        )
                        Text(text = mode.displayName, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SettingsActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}
