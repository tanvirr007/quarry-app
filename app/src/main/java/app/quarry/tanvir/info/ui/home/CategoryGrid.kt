package app.quarry.tanvir.info.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.quarry.tanvir.info.domain.analyzer.StorageCategoryData
import app.quarry.tanvir.info.domain.haptics.rememberQuarryHaptic
import app.quarry.tanvir.info.domain.model.StorageCategory
import app.quarry.tanvir.info.domain.model.StorageFormatter
import app.quarry.tanvir.info.ui.components.getColor
import app.quarry.tanvir.info.ui.components.getIcon

@Composable
fun CategoryGrid(
    categories: List<StorageCategoryData>,
    onCategoryClick: (StorageCategory) -> Unit,
    onCategoryLongClick: ((StorageCategory) -> Unit)? = null,
    hapticsEnabled: Boolean = true,
    hapticStrength: Int = 60,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Storage Categories",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
        )

        val pairs = categories.chunked(2)
        pairs.forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CategoryCard(
                    categoryData = pair[0],
                    onClick = { onCategoryClick(pair[0].category) },
                    onLongClick = onCategoryLongClick?.let { { it(pair[0].category) } },
                    hapticsEnabled = hapticsEnabled,
                    hapticStrength = hapticStrength,
                    modifier = Modifier.weight(1f)
                )

                if (pair.size > 1) {
                    CategoryCard(
                        categoryData = pair[1],
                        onClick = { onCategoryClick(pair[1].category) },
                        onLongClick = onCategoryLongClick?.let { { it(pair[1].category) } },
                        hapticsEnabled = hapticsEnabled,
                        hapticStrength = hapticStrength,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryCard(
    categoryData: StorageCategoryData,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    hapticsEnabled: Boolean = true,
    hapticStrength: Int = 60,
    modifier: Modifier = Modifier
) {
    val categoryColor = categoryData.category.getColor()
    val quarryHaptic = rememberQuarryHaptic(hapticsEnabled, hapticStrength)

    Card(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    quarryHaptic()
                    onLongClick?.invoke()
                }
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(categoryColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = categoryData.category.getIcon(),
                        contentDescription = categoryData.category.displayName,
                        tint = categoryColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (categoryData.fileCount > 0) {
                    Text(
                        text = if (categoryData.category == StorageCategory.APPS) {
                            StorageFormatter.formatAppCount(categoryData.fileCount)
                        } else {
                            StorageFormatter.formatFileCount(categoryData.fileCount)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = categoryData.category.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = StorageFormatter.formatBytes(categoryData.totalBytes),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
