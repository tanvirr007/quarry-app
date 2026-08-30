package app.quarry.tanvir.info.ui.cleanup

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.quarry.tanvir.info.domain.file.TrashItem
import app.quarry.tanvir.info.domain.model.StorageCategory
import app.quarry.tanvir.info.domain.model.StorageFormatter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import app.quarry.tanvir.info.ui.components.FileThumbnail
import app.quarry.tanvir.info.ui.components.getColor
import app.quarry.tanvir.info.ui.components.getIcon
import app.quarry.tanvir.info.ui.components.rememberBlockNestedScrollConnection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashManagementDialog(
    trashItems: List<TrashItem>,
    onRestoreItem: (String) -> Unit,
    onRestoreSelected: (List<String>) -> Unit,
    onDeleteForever: (String) -> Unit,
    onDeleteSelectedForever: (List<String>) -> Unit,
    onEmptyTrash: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val blockNestedScrollConnection = rememberBlockNestedScrollConnection()
    val haptics = app.quarry.tanvir.info.domain.haptics.LocalQuarryHaptics.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var itemPendingRestore by remember { mutableStateOf<TrashItem?>(null) }
    var itemPendingPermanentDelete by remember { mutableStateOf<TrashItem?>(null) }
    var showEmptyTrashConfirmDialog by remember { mutableStateOf(false) }
    var showBulkRestoreConfirmDialog by remember { mutableStateOf(false) }
    var showBulkDeleteConfirmDialog by remember { mutableStateOf(false) }

    val totalBytes = trashItems.sumOf { it.size }
    val filteredItems = if (searchQuery.isBlank()) {
        trashItems
    } else {
        trashItems.filter {
            app.quarry.tanvir.info.domain.model.SearchMatcher.matches(it.name, it.originalPath, searchQuery)
        }
    }
    val selectedItems = trashItems.filter { selectedIds.contains(it.id) }
    val selectedBytes = selectedItems.sumOf { it.size }
    val isSelectionMode = selectedIds.isNotEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        properties = ModalBottomSheetDefaults.properties(shouldDismissOnBackPress = false),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        val hasActiveTrashDialogState = showEmptyTrashConfirmDialog ||
                showBulkRestoreConfirmDialog ||
                showBulkDeleteConfirmDialog ||
                itemPendingRestore != null ||
                itemPendingPermanentDelete != null ||
                selectedIds.isNotEmpty() ||
                searchQuery.isNotEmpty()

        BackHandler(enabled = true) {
            haptics.click()
            when {
                showEmptyTrashConfirmDialog -> showEmptyTrashConfirmDialog = false
                showBulkRestoreConfirmDialog -> showBulkRestoreConfirmDialog = false
                showBulkDeleteConfirmDialog -> showBulkDeleteConfirmDialog = false
                itemPendingRestore != null -> itemPendingRestore = null
                itemPendingPermanentDelete != null -> itemPendingPermanentDelete = null
                selectedIds.isNotEmpty() -> selectedIds = emptySet()
                searchQuery.isNotEmpty() -> searchQuery = ""
                else -> onDismiss()
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Dynamic Header: Switches cleanly between Normal mode and Selection mode
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (isSelectionMode) {
                    // Selection mode header
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${selectedIds.size} Selected",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${StorageFormatter.formatBytes(selectedBytes)} of ${StorageFormatter.formatBytes(totalBytes)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = {
                            haptics.click()
                            selectedIds = emptySet()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear Selection",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    // Normal mode header
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Trash",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${trashItems.size} items · ${StorageFormatter.formatBytes(totalBytes)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (trashItems.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                haptics.warning()
                                showEmptyTrashConfirmDialog = true
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Empty Trash",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            // Search Bar (when trash has multiple items)
            if (trashItems.isNotEmpty() && (trashItems.size > 3 || searchQuery.isNotBlank())) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search trash…", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                haptics.click()
                                searchQuery = ""
                            }) {
                                Icon(imageVector = Icons.Rounded.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                    ),
                    singleLine = true
                )
            }

            // Selection Action Toolbar / Quick Toggle Row
            if (trashItems.isNotEmpty()) {
                val allSelected = selectedIds.size == filteredItems.size && filteredItems.isNotEmpty()

                if (isSelectionMode) {
                    // Dedicated Selection Action Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(
                                onClick = {
                                    haptics.selection()
                                    selectedIds = if (allSelected) emptySet() else filteredItems.map { it.id }.toSet()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (allSelected) "Deselect All" else "Select All",
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilledTonalButton(
                                    onClick = {
                                        haptics.click()
                                        showBulkRestoreConfirmDialog = true
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.RestoreFromTrash,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Restore",
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }

                                Button(
                                    onClick = {
                                        haptics.warning()
                                        showBulkDeleteConfirmDialog = true
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteForever,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Delete",
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Normal state quick select toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${filteredItems.size} items available",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = {
                                selectedIds = filteredItems.map { it.id }.toSet()
                            }
                        ) {
                            Text("Select All")
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Main Content Area
            if (trashItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Text(
                            text = "Trash is Empty",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Files you move to Trash will be preserved here securely. You can restore them anytime or empty trash to reclaim storage.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No trash items match \"$searchQuery\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { searchQuery = "" }) {
                            Text("Clear filter")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .nestedScroll(blockNestedScrollConnection),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        val isSelected = selectedIds.contains(item.id)
                        val ext = item.name.substringAfterLast('.', "")
                        val category = StorageCategory.fromExtension(ext)
                        val icon = category.getIcon()
                        val color = category.getColor()
                        val deletedTimeFormatted = formatRelativeDeletedTime(item.deletedTimestamp)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    haptics.selection()
                                    selectedIds = if (isSelected) {
                                        selectedIds - item.id
                                    } else {
                                        selectedIds + item.id
                                    }
                                },
                            shape = RoundedCornerShape(14.dp),
                            border = if (isSelected) {
                                BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                            } else {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        haptics.selection()
                                        selectedIds = if (checked) selectedIds + item.id else selectedIds - item.id
                                    }
                                )

                                val thumbnailPath = item.trashPath.ifEmpty { item.originalPath }
                                FileThumbnail(
                                    path = thumbnailPath,
                                    category = category,
                                    size = 40.dp,
                                    shape = RoundedCornerShape(10.dp),
                                    lastModified = item.deletedTimestamp,
                                    isDirectory = item.isDirectory
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${StorageFormatter.formatBytes(item.size)} · $deletedTimeFormatted",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.originalPath,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // In normal mode (not multi-selecting), show individual restore & delete icons
                                if (!isSelectionMode) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        IconButton(
                                            onClick = {
                                                haptics.click()
                                                itemPendingRestore = item
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.RestoreFromTrash,
                                                contentDescription = "Restore",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                haptics.warning()
                                                itemPendingPermanentDelete = item
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DeleteForever,
                                                contentDescription = "Delete Forever",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Close button
            OutlinedButton(
                onClick = {
                    haptics.click()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Close")
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }

    // Confirmation Alert for Emptying Trash
    if (showEmptyTrashConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashConfirmDialog = false },
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            title = {
                Text("Empty Trash?")
            },
            text = {
                Text(
                    "All ${trashItems.size} items (${StorageFormatter.formatBytes(totalBytes)}) will be permanently erased from storage. This cannot be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        haptics.warning()
                        showEmptyTrashConfirmDialog = false
                        onEmptyTrash()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Empty Trash")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    haptics.click()
                    showEmptyTrashConfirmDialog = false
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirmation Alert for Single Item Restore
    itemPendingRestore?.let { item ->
        AlertDialog(
            onDismissRequest = { itemPendingRestore = null },
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RestoreFromTrash,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            title = {
                Text("Restore File?")
            },
            text = {
                Text(
                    "\"${item.name}\" (${StorageFormatter.formatBytes(item.size)}) will be restored to its original location:\n${item.originalPath}"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        haptics.click()
                        val id = item.id
                        itemPendingRestore = null
                        onRestoreItem(id)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    haptics.click()
                    itemPendingRestore = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirmation Alert for Bulk Restore Selected
    if (showBulkRestoreConfirmDialog) {
        val count = selectedIds.size
        val bytes = selectedBytes
        AlertDialog(
            onDismissRequest = { showBulkRestoreConfirmDialog = false },
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RestoreFromTrash,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            title = {
                Text("Restore $count items?")
            },
            text = {
                Text(
                    "$count selected items (${StorageFormatter.formatBytes(bytes)}) will be restored to their original locations."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        haptics.click()
                        val toRestore = selectedIds.toList()
                        showBulkRestoreConfirmDialog = false
                        selectedIds = emptySet()
                        onRestoreSelected(toRestore)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    haptics.click()
                    showBulkRestoreConfirmDialog = false
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirmation Alert for Single Item Permanent Delete
    itemPendingPermanentDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemPendingPermanentDelete = null },
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteForever,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            title = {
                Text("Delete Forever?")
            },
            text = {
                Text(
                    "\"${item.name}\" (${StorageFormatter.formatBytes(item.size)}) will be permanently deleted from disk. This action cannot be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        haptics.warning()
                        val id = item.id
                        itemPendingPermanentDelete = null
                        onDeleteForever(id)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Delete Forever")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    haptics.click()
                    itemPendingPermanentDelete = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirmation Alert for Bulk Delete Selected
    if (showBulkDeleteConfirmDialog) {
        val count = selectedIds.size
        val bytes = selectedBytes
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text("Delete $count items forever?")
            },
            text = {
                Text(
                    "$count selected items (${StorageFormatter.formatBytes(bytes)}) will be permanently removed from disk. This cannot be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        haptics.warning()
                        val toDelete = selectedIds.toList()
                        showBulkDeleteConfirmDialog = false
                        selectedIds = emptySet()
                        onDeleteSelectedForever(toDelete)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Delete Forever")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    haptics.click()
                    showBulkDeleteConfirmDialog = false
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun formatRelativeDeletedTime(timestamp: Long): String {
    if (timestamp <= 0) return "Unknown date"
    val diff = System.currentTimeMillis() - timestamp
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)

    return when {
        minutes < 1 -> "Deleted just now"
        minutes < 60 -> "Deleted $minutes min ago"
        hours < 24 -> "Deleted $hours hr ago"
        days == 1L -> "Deleted yesterday"
        days < 30 -> "Deleted $days days ago"
        else -> {
            SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(timestamp))
        }
    }
}
