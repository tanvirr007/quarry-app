package app.quarry.tanvir.info.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.quarry.tanvir.info.ui.components.QuarryFullScreenDialog
import app.quarry.tanvir.info.domain.model.StorageCategory
import app.quarry.tanvir.info.ui.components.getColor
import app.quarry.tanvir.info.ui.components.getIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryVisibilityDialog(
    enabledCategories: Set<String>,
    onToggleCategory: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = app.quarry.tanvir.info.domain.haptics.LocalQuarryHaptics.current

    QuarryFullScreenDialog(
        onDismissRequest = onDismiss
    ) {
        BackHandler(onBack = {
            haptics.click()
            onDismiss()
        })
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Storage Categories", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = {
                            haptics.click()
                            onDismiss()
                        }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            haptics.click()
                            onDismiss()
                        }) {
                            Icon(Icons.Rounded.Check, contentDescription = "Done")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "${enabledCategories.size} of ${StorageCategory.entries.size} visible · At least 4 required",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
                StorageCategory.entries.forEach { category ->
                    val enabled = enabledCategories.contains(category.name)
                    val canDisable = enabledCategories.size > 4 || !enabled
                    CategoryToggleRow(
                        category = category,
                        enabled = enabled,
                        canDisable = canDisable,
                        onToggle = { onToggleCategory(category.name) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryToggleRow(
    category: StorageCategory,
    enabled: Boolean,
    canDisable: Boolean,
    onToggle: () -> Unit
) {
    val haptics = app.quarry.tanvir.info.domain.haptics.LocalQuarryHaptics.current
    val color = category.getColor()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = canDisable || !enabled) {
                if (canDisable || !enabled) {
                    haptics.selection()
                    onToggle()
                }
            },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = category.getIcon(),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (enabled) "Visible on Home" else "Hidden",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Checkbox(
                checked = enabled,
                onCheckedChange = {
                    if (canDisable || !enabled) {
                        haptics.selection()
                        onToggle()
                    }
                },
                enabled = canDisable || !enabled
            )
        }
    }
}
