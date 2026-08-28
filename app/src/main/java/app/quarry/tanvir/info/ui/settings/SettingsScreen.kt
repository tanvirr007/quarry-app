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
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.quarry.tanvir.info.data.preferences.ThemeMode

import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.remember

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    val packageInfo = remember(context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
        } catch (e: Exception) {
            null
        }
    }

    val versionName = packageInfo?.versionName ?: "1.0.0"
    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo?.longVersionCode ?: 1L
    } else {
        packageInfo?.versionCode?.toLong() ?: 1L
    }

    // Prioritized Back handling: Dev Info -> Theme Dialog -> Volumes Dialog -> Exclusions Dialog -> System (Home)
    val hasActiveSettingsDialog = uiState.isDevInfoVisible ||
            uiState.isThemeDialogVisible ||
            uiState.isVolumesDialogVisible ||
            uiState.isExclusionsDialogVisible

    BackHandler(enabled = hasActiveSettingsDialog) {
        when {
            uiState.isDevInfoVisible -> viewModel.hideDevInfo()
            uiState.isThemeDialogVisible -> viewModel.hideThemeDialog()
            uiState.isVolumesDialogVisible -> viewModel.hideVolumesDialog()
            uiState.isExclusionsDialogVisible -> viewModel.hideExclusionsDialog()
        }
    }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearUserMessage()
        }
    }

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

        // 1. Appearance
        SettingsActionItem(
            icon = Icons.Rounded.DarkMode,
            title = "Appearance",
            subtitle = uiState.themeMode.displayName,
            onClick = { viewModel.showThemeDialog() }
        )

        // 2. Security & Biometrics
        SettingsSwitchItem(
            icon = Icons.Rounded.Fingerprint,
            title = "Biometric Lock",
            subtitle = "Protect trash, delete, rename",
            checked = uiState.isBiometricEnabled,
            onCheckedChange = { viewModel.toggleBiometricProtection(activity, it) }
        )

        // 3. Scan & Exclusions
        val exclusionsSubtitle = if (uiState.excludedFolders.isEmpty()) "None excluded" else "${uiState.excludedFolders.size} excluded"
        SettingsActionItem(
            icon = Icons.Rounded.Tune,
            title = "Excluded Folders",
            subtitle = exclusionsSubtitle,
            onClick = { viewModel.showExclusionsDialog() }
        )

        // 4. Hidden Files & Folders
        SettingsSwitchItem(
            icon = Icons.Rounded.Visibility,
            title = "Hidden Files",
            subtitle = "Include dotfiles in scans",
            checked = uiState.scanHiddenFiles,
            onCheckedChange = { viewModel.setScanHiddenFiles(it) }
        )

        // 5. Storage Volumes
        val volumeCount = uiState.detectedVolumes.size
        val volumesSubtitle = if (volumeCount <= 1) {
            "Internal Storage"
        } else {
            "$volumeCount volumes detected"
        }
        SettingsActionItem(
            icon = Icons.Rounded.SdStorage,
            title = "Storage Volumes",
            subtitle = volumesSubtitle,
            onClick = { viewModel.showVolumesDialog() }
        )

        Text(
            text = "About & Help",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp)
        )

        // 6. About Quarry
        SettingsActionItem(
            icon = Icons.Rounded.Info,
            title = "About Quarry",
            subtitle = "Version $versionName (Build $versionCode)",
            onClick = { viewModel.showDevInfo() }
        )
    }

    // Developer Info Full-Screen Dialog
    if (uiState.isDevInfoVisible) {
        DeveloperInfoDialog(
            onDismiss = { viewModel.hideDevInfo() }
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

    // Exclusions Dialog
    if (uiState.isExclusionsDialogVisible) {
        ExclusionsDialog(
            excludedFolders = uiState.excludedFolders,
            onAddExclusion = { viewModel.addExclusion(it) },
            onAddExclusions = { viewModel.addExclusions(it) },
            onRemoveExclusion = { viewModel.removeExclusion(it) },
            onDismiss = { viewModel.hideExclusionsDialog() }
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
    subtitle: String? = null,
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

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
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
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onCheckedChange(!checked) },
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
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}
