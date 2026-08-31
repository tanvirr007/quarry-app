package app.quarry.tanvir.info.ui.explore

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.quarry.tanvir.info.data.database.FileEntity
import app.quarry.tanvir.info.domain.model.StorageCategory
import app.quarry.tanvir.info.domain.model.StorageFormatter
import app.quarry.tanvir.info.ui.components.getColor
import app.quarry.tanvir.info.ui.components.getIcon

@Composable
fun FileTypeGroupView(
    files: List<FileEntity>,
    sortOrder: FileSortOrder = FileSortOrder.SIZE_DESC,
    onCategoryClick: (StorageCategory, List<FileEntity>) -> Unit,
    modifier: Modifier = Modifier
) {
    if (files.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No files found to categorize.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val nonDirFiles = files.filter { !it.isDirectory }
    val baseGrouped = StorageCategory.entries.mapNotNull { category ->
        val catFiles = nonDirFiles
            .filter { StorageCategory.fromExtension(it.extension) == category }
        if (catFiles.isNotEmpty()) {
            category to catFiles
        } else null
    }

    val grouped = when (sortOrder) {
        FileSortOrder.SIZE_DESC -> baseGrouped.sortedByDescending { (_, catFiles) -> catFiles.sumOf { it.size } }
        FileSortOrder.SIZE_ASC -> baseGrouped.sortedBy { (_, catFiles) -> catFiles.sumOf { it.size } }
        FileSortOrder.NAME_ASC -> baseGrouped.sortedBy { (category, _) -> category.displayName }
        FileSortOrder.NAME_DESC -> baseGrouped.sortedByDescending { (category, _) -> category.displayName }
        FileSortOrder.DATE_DESC -> baseGrouped.sortedByDescending { (_, catFiles) -> catFiles.maxOfOrNull { it.lastModified } ?: 0L }
        FileSortOrder.DATE_ASC -> baseGrouped.sortedBy { (_, catFiles) -> catFiles.minOfOrNull { it.lastModified } ?: 0L }
    }

    if (grouped.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No categorized files found.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val maxCategoryBytes = grouped.maxOfOrNull { (_, catFiles) -> catFiles.sumOf { it.size } }?.coerceAtLeast(1L) ?: 1L
    val haptics = app.quarry.tanvir.info.domain.haptics.LocalQuarryHaptics.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(grouped, key = { it.first.name }) { (category, catFiles) ->
            val icon = category.getIcon()
            val color = category.getColor()
            val totalBytes = catFiles.sumOf { it.size }
            val progressFraction = (totalBytes.toFloat() / maxCategoryBytes.toFloat()).coerceIn(0.01f, 1f)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        haptics.click()
                        onCategoryClick(category, catFiles)
                    },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(color.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = color,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = category.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${catFiles.size} files · ${StorageFormatter.formatBytes(totalBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = "View category files",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = color,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round
                    )
                }
            }
        }
    }
}
