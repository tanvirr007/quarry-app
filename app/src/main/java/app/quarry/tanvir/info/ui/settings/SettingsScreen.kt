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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BedtimeOff
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import app.quarry.tanvir.info.domain.haptics.hapticStrengthLabel
import app.quarry.tanvir.info.domain.haptics.performQuarryHaptic

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

    // Prioritized Back handling: Dev Info -> Theme/Category/Volumes/Exclusions -> System (Home)
    val hasActiveSettingsDialog = uiState.isDevInfoVisible ||
            uiState.isThemeDialogVisible ||
            uiState.isVolumesDialogVisible ||
            uiState.isExclusionsDialogVisible ||
            uiState.isCategoryDialogVisible

    BackHandler(enabled = hasActiveSettingsDialog) {
        when {
            uiState.isDevInfoVisible -> viewModel.hideDevInfo()
            uiState.isCategoryDialogVisible -> viewModel.hideCategoryDialog()
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
            icon = if (uiState.scanHiddenFiles) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
            title = "Show Hidden Files",
            subtitle = if (uiState.scanHiddenFiles) "Hidden items visible" else "Hidden items hidden",
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

        // 6. Miscellaneous (above About)
        MiscellaneousSection(
            isQuickInsightsEnabled = uiState.isQuickInsightsEnabled,
            onToggleQuickInsights = { viewModel.setQuickInsightsEnabled(it) },
            enabledCategories = uiState.enabledCategories,
            onManageCategories = { viewModel.showCategoryDialog() },
            isHapticsEnabled = uiState.isHapticsEnabled,
            hapticStrength = uiState.hapticStrength,
            onToggleHaptics = { viewModel.setHapticsEnabled(it) },
            onStrengthChange = { viewModel.setHapticStrength(it) },
            isKeepScreenOn = uiState.isKeepScreenOn,
            onToggleKeepScreenOn = { viewModel.setKeepScreenOn(it) }
        )

        // 7. About
        SettingsActionItem(
            icon = Icons.Rounded.Info,
            title = "About",
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

    // Appearance Full-Screen Dialog
    if (uiState.isThemeDialogVisible) {
        AppearanceDialog(
            currentTheme = uiState.themeMode,
            isDynamicColor = uiState.isDynamicColorEnabled,
            onSelectTheme = { viewModel.setThemeMode(it) },
            onToggleDynamicColor = { viewModel.toggleDynamicColor(it) },
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

    if (uiState.isCategoryDialogVisible) {
        CategoryVisibilityDialog(
            enabledCategories = uiState.enabledCategories,
            onToggleCategory = { viewModel.toggleCategory(it) },
            onDismiss = { viewModel.hideCategoryDialog() }
        )
    }
}

@Composable
private fun MiscellaneousSection(
    isQuickInsightsEnabled: Boolean,
    onToggleQuickInsights: (Boolean) -> Unit,
    enabledCategories: Set<String>,
    onManageCategories: () -> Unit,
    isHapticsEnabled: Boolean,
    hapticStrength: Int,
    onToggleHaptics: (Boolean) -> Unit,
    onStrengthChange: (Int) -> Unit,
    isKeepScreenOn: Boolean,
    onToggleKeepScreenOn: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var sliderValue by remember(hapticStrength) { mutableFloatStateOf(hapticStrength.toFloat()) }
    var sliderDraft by remember { mutableStateOf<Int?>(null) }
    val displayStrength = sliderDraft ?: hapticStrength

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Miscellaneous",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            // Quick Insights toggle
            MiscSwitchRow(
                icon = Icons.Rounded.Lightbulb,
                title = "Quick Insights",
                subtitle = if (isQuickInsightsEnabled) "Visible on Home" else "Hidden on Home",
                checked = isQuickInsightsEnabled,
                onCheckedChange = onToggleQuickInsights
            )

            // Storage Categories manager
            MiscActionRow(
                icon = Icons.Rounded.GridView,
                title = "Storage Categories",
                subtitle = "${enabledCategories.size} of 8 visible · Tap to manage",
                onClick = onManageCategories
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

            // Vibration toggle + strength slider
            MiscSwitchRow(
                icon = Icons.Rounded.Vibration,
                title = "Haptic Feedback",
                subtitle = if (isHapticsEnabled) "Vibration on long-press" else "Vibration disabled",
                checked = isHapticsEnabled,
                onCheckedChange = onToggleHaptics
            )
            if (isHapticsEnabled) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 54.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Strength",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${displayStrength} · ${hapticStrengthLabel(displayStrength)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = sliderValue,
                        onValueChange = {
                            sliderValue = it
                            sliderDraft = it.toInt().coerceIn(1, 100)
                        },
                        onValueChangeFinished = {
                            val final = sliderValue.toInt().coerceIn(1, 100)
                            sliderDraft = null
                            onStrengthChange(final)
                            context.performQuarryHaptic(final)
                        },
                        valueRange = 1f..100f,
                        steps = 98
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

            // Keep screen on
            MiscSwitchRow(
                icon = Icons.Rounded.BedtimeOff,
                title = "Keep Screen On",
                subtitle = if (isKeepScreenOn) "Screen stays on while app is open" else "Screen may dim normally",
                checked = isKeepScreenOn,
                onCheckedChange = onToggleKeepScreenOn
            )
        }
    }
}

@Composable
private fun MiscSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onCheckedChange(!checked) }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun MiscActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onClick() }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
    }
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
