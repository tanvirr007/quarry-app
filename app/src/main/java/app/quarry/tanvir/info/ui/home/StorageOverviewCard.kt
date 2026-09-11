package app.quarry.tanvir.info.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.quarry.tanvir.info.domain.analyzer.StorageOverviewData
import app.quarry.tanvir.info.domain.haptics.LocalQuarryHaptics
import app.quarry.tanvir.info.domain.model.StorageFormatter
import app.quarry.tanvir.info.domain.volume.StorageDeviceType
import app.quarry.tanvir.info.domain.volume.StorageVolumeInfo
import app.quarry.tanvir.info.ui.components.getColor

@Composable
fun StorageOverviewCard(
    overview: StorageOverviewData,
    modifier: Modifier = Modifier,
    availableVolumes: List<StorageVolumeInfo> = emptyList(),
    selectedVolume: StorageVolumeInfo? = null,
    onSelectVolume: ((StorageVolumeInfo) -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val usedPercentageAnim by animateFloatAsState(
        targetValue = overview.usedPercentage,
        animationSpec = tween(durationMillis = 800),
        label = "usedPercentage"
    )
    val haptics = LocalQuarryHaptics.current
    val isMultiVolume = availableVolumes.size > 1
    var dropdownExpanded by remember { mutableStateOf(false) }

    val volumeIcon = when (selectedVolume?.deviceType) {
        StorageDeviceType.SD_CARD -> Icons.Rounded.SdCard
        StorageDeviceType.USB_OTG -> Icons.Rounded.Usb
        else -> Icons.Rounded.Smartphone
    }

    Card(
        onClick = {
            haptics.click()
            onClick?.invoke()
        },
        enabled = onClick != null,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Volume Name + Status Chip / Volume Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = volumeIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = overview.volumeName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (overview.totalFiles > 0) {
                            Text(
                                text = StorageFormatter.formatFileCount(overview.totalFiles),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Right side: storage usage % and action chevron / volume switcher
                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .then(
                                if (isMultiVolume) {
                                    Modifier.clickable {
                                        haptics.click()
                                        dropdownExpanded = true
                                    }
                                } else if (onClick != null) {
                                    Modifier.clickable {
                                        haptics.click()
                                        onClick()
                                    }
                                } else Modifier
                            )
                            .padding(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${(usedPercentageAnim * 100).toInt()}% used",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (isMultiVolume) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                    .padding(2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowDropDown,
                                    contentDescription = "Switch storage volume",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else if (onClick != null) {
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = "Explore files",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (isMultiVolume) {
                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false }
                        ) {
                            availableVolumes.forEach { vol ->
                                val isSelected = vol.id == selectedVolume?.id
                                val itemIcon = when (vol.deviceType) {
                                    StorageDeviceType.SD_CARD -> Icons.Rounded.SdCard
                                    StorageDeviceType.USB_OTG -> Icons.Rounded.Usb
                                    else -> Icons.Rounded.Smartphone
                                }

                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = vol.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                            Text(
                                                text = if (vol.totalBytes > 0) {
                                                    "${StorageFormatter.formatBytes(vol.freeBytes)} free of ${StorageFormatter.formatBytes(vol.totalBytes)}"
                                                } else {
                                                    "Storage Access Framework"
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = itemIcon,
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    },
                                    trailingIcon = if (isSelected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    } else null,
                                    onClick = {
                                        haptics.click()
                                        dropdownExpanded = false
                                        onSelectVolume?.invoke(vol)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Stats numbers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = StorageFormatter.formatBytes(overview.usedBytes),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${StorageFormatter.formatBytes(overview.freeBytes)} free",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "Total: ${StorageFormatter.formatBytes(overview.totalBytes)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Multi-segment category bar
            MultiSegmentStorageBar(
                overview = overview,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
            )

            // Storage Growth Indicator
            if (overview.storageGrowthText != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.TrendingUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = overview.storageGrowthText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun MultiSegmentStorageBar(
    overview: StorageOverviewData,
    modifier: Modifier = Modifier
) {
    val totalCapacity = overview.totalBytes.coerceAtLeast(1L).toFloat()

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            val nonZeroCategories = overview.categoryBreakdown.filter { it.totalBytes > 0 }

            if (nonZeroCategories.isEmpty()) {
                // If not scanned yet, show standard used block
                val usedRatio = (overview.usedBytes.toFloat() / totalCapacity).coerceIn(0f, 1f)
                if (usedRatio > 0f) {
                    Box(
                        modifier = Modifier
                            .weight(usedRatio)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
                val freeRatio = (1f - usedRatio).coerceAtLeast(0.001f)
                Box(
                    modifier = Modifier
                        .weight(freeRatio)
                        .fillMaxHeight()
                        .background(Color.Transparent)
                )
            } else {
                // Show each category as a proportional segment
                nonZeroCategories.forEach { categoryData ->
                    val ratio = (categoryData.totalBytes.toFloat() / totalCapacity).coerceAtLeast(0.002f)
                    Box(
                        modifier = Modifier
                            .weight(ratio)
                            .fillMaxHeight()
                            .background(categoryData.category.getColor())
                    )
                }

                // Remaining free space
                val remainingBytes = overview.freeBytes.toFloat()
                val freeRatio = (remainingBytes / totalCapacity).coerceIn(0.001f, 1f)
                Box(
                    modifier = Modifier
                        .weight(freeRatio)
                        .fillMaxHeight()
                        .background(Color.Transparent)
                )
            }
        }
    }
}
