package app.quarry.tanvir.info.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Sort
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
import app.quarry.tanvir.info.domain.haptics.rememberQuarryHaptic
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.quarry.tanvir.info.data.database.FileEntity
import app.quarry.tanvir.info.domain.model.StorageCategory
import app.quarry.tanvir.info.domain.model.StorageFormatter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import app.quarry.tanvir.info.ui.components.getColor
import app.quarry.tanvir.info.ui.components.getIcon
import app.quarry.tanvir.info.ui.components.rememberBlockNestedScrollConnection
import app.quarry.tanvir.info.ui.explore.DeleteCountdownDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import app.quarry.tanvir.info.domain.haptics.LocalQuarryHaptics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeItemListBottomSheet(
    title: String,
    category: StorageCategory,
    files: List<FileEntity>,
    startInSelectionMode: Boolean = false,
    hapticsEnabled: Boolean = true,
    hapticStrength: Int = 60,
    onFileClick: (FileEntity) -> Unit,
    onMoveToTrashSelected: (List<FileEntity>, () -> Unit) -> Unit,
    onDeleteSelected: (List<FileEntity>, () -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    var sortByLargest by remember { mutableStateOf(true) }
    var isSelectionMode by remember(startInSelectionMode) { mutableStateOf(startInSelectionMode) }
    var selectedPaths by remember { mutableStateOf(setOf<String>()) }
    var deleteCandidates by remember { mutableStateOf<List<FileEntity>?>(null) }

    val categoryColor = category.getColor()
    val totalBytes = files.sumOf { it.size }
    val haptics = LocalQuarryHaptics.current
    val blockNestedScrollConnection = rememberBlockNestedScrollConnection()

    val filteredFiles = remember(files, searchQuery, sortByLargest) {
        val list = if (searchQuery.isBlank()) {
            files
        } else {
            files.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
        }
        if (sortByLargest) {
            list.sortedByDescending { it.size }
        } else {
            list.sortedByDescending { it.lastModified }
        }
    }

    val selectedFiles = remember(files, selectedPaths) {
        files.filter { selectedPaths.contains(it.path) }
    }
    val selectedBytes = remember(selectedFiles) {
        selectedFiles.sumOf { it.size }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Switches dynamically between standard header and selection mode header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (isSelectionMode) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "${selectedPaths.size} selected",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (selectedPaths.isNotEmpty()) {
                                    StorageFormatter.formatBytes(selectedBytes)
                                } else {
                                    "Tap items to mark"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        val isAllSelected = filteredFiles.isNotEmpty() && filteredFiles.all { selectedPaths.contains(it.path) }
                        IconButton(
                            onClick = {
                                haptics.selection()
                                if (isAllSelected) {
                                    selectedPaths = emptySet()
                                } else {
                                    selectedPaths = filteredFiles.map { it.path }.toSet()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isAllSelected) Icons.Rounded.Deselect else Icons.Rounded.SelectAll,
                                contentDescription = if (isAllSelected) "Deselect All" else "Select All",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = {
                                haptics.click()
                                isSelectionMode = false
                                selectedPaths = emptySet()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Cancel selection",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(categoryColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = category.getIcon(),
                                contentDescription = null,
                                tint = categoryColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${files.size} files · ${StorageFormatter.formatBytes(totalBytes)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                haptics.click()
                                sortByLargest = !sortByLargest
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Sort,
                                contentDescription = if (sortByLargest) "Sorted by Size" else "Sorted by Date",
                                tint = if (sortByLargest) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            haptics.click()
                            onDismiss()
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Search Bar
            if (files.size > 3) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filter ${title.lowercase()}…") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                haptics.click()
                                searchQuery = ""
                            }) {
                                Icon(imageVector = Icons.Rounded.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // File List
            if (filteredFiles.isEmpty()) {
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
                        Icon(
                            imageVector = Icons.Rounded.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = if (searchQuery.isNotBlank()) "No matching files found" else "No files in this section",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                    items(filteredFiles, key = { it.path }) { file ->
                        val isSelected = selectedPaths.contains(file.path)

                        FileItemCard(
                            file = file,
                            isSelected = isSelected,
                            isSelectionMode = isSelectionMode,
                            onClick = {
                                if (isSelectionMode) {
                                    haptics.selection()
                                    selectedPaths = if (isSelected) {
                                        selectedPaths - file.path
                                    } else {
                                        selectedPaths + file.path
                                    }
                                } else {
                                    haptics.click()
                                    onFileClick(file)
                                }
                            },
                            onLongClick = {
                                haptics.longPress()
                                if (!isSelectionMode) {
                                    isSelectionMode = true
                                    selectedPaths = setOf(file.path)
                                } else {
                                    selectedPaths = if (isSelected) {
                                        selectedPaths - file.path
                                    } else {
                                        selectedPaths + file.path
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // Bottom Action Bar for Bulk Selection (Trash / Delete Permanently)
            AnimatedVisibility(
                visible = isSelectionMode,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            if (selectedFiles.isNotEmpty()) {
                                haptics.warning()
                                onMoveToTrashSelected(selectedFiles) {
                                    selectedPaths = emptySet()
                                    isSelectionMode = false
                                }
                            }
                        },
                        enabled = selectedPaths.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RestoreFromTrash,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (selectedPaths.isEmpty()) "To Trash" else "To Trash (${selectedPaths.size})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Button(
                        onClick = {
                            if (selectedFiles.isNotEmpty()) {
                                deleteCandidates = selectedFiles
                            }
                        },
                        enabled = selectedPaths.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteForever,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (selectedPaths.isEmpty()) "Delete" else "Delete (${StorageFormatter.formatBytes(selectedBytes)})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
        }
    }

    // Delete Confirmation Dialog
    deleteCandidates?.let { candidates ->
        DeleteCountdownDialog(
            candidates = candidates,
            isBiometricAuthRequired = true,
            onDismiss = { deleteCandidates = null },
            onConfirmAuthenticatedDelete = {
                val toDelete = candidates
                deleteCandidates = null
                onDeleteSelected(toDelete) {
                    selectedPaths = emptySet()
                    isSelectionMode = false
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileItemCard(
    file: FileEntity,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val fileCategory = StorageCategory.fromExtension(file.extension)
    val color = fileCategory.getColor()
    val icon = fileCategory.getIcon()
    val dateStr = if (file.lastModified > 0) {
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(file.lastModified))
    } else ""

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(14.dp),
        border = if (isSelected) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f))
        } else null,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
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
            AnimatedVisibility(
                visible = isSelectionMode,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.padding(end = 2.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (dateStr.isNotEmpty()) "$dateStr · ${file.path}" else file.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = StorageFormatter.formatBytes(file.size),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
