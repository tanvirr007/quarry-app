package app.quarry.tanvir.info.ui.explore

import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.quarry.tanvir.info.domain.model.StorageCategory
import app.quarry.tanvir.info.domain.model.StorageFormatter

@Composable
fun ExploreScreen(
    modifier: Modifier = Modifier,
    viewModel: ExploreViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val directoryFiles by viewModel.directoryFiles.collectAsStateWithLifecycle()
    val allCategorizedFiles by viewModel.allCategorizedFiles.collectAsStateWithLifecycle()
    val treemapNodes by viewModel.treemapLayoutNodes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    // Prioritized Back handling: Dialogs -> Details Sheet -> Selection -> Search -> Folder Hierarchy -> System (Home)
    val isFolderViewMode = uiState.viewMode == ExploreViewMode.TREEMAP ||
            uiState.viewMode == ExploreViewMode.LIST ||
            uiState.viewMode == ExploreViewMode.FOLDERS
    val canNavigateUpFolder = isFolderViewMode &&
            uiState.currentPath != Environment.getExternalStorageDirectory().absolutePath

    val hasActiveExploreState = uiState.isDeleteCountdownVisible ||
            uiState.activeRenameFile != null ||
            uiState.activeDetailsFile != null ||
            uiState.isSelectionMode ||
            uiState.searchQuery.isNotEmpty() ||
            canNavigateUpFolder

    val haptics = app.quarry.tanvir.info.domain.haptics.LocalQuarryHaptics.current

    BackHandler(enabled = hasActiveExploreState) {
        haptics.click()
        when {
            uiState.isDeleteCountdownVisible -> viewModel.dismissDeleteDialog()
            uiState.activeRenameFile != null -> viewModel.dismissRename()
            uiState.activeDetailsFile != null -> viewModel.hideDetails()
            uiState.isSelectionMode -> viewModel.clearSelection()
            uiState.searchQuery.isNotEmpty() -> viewModel.setSearchQuery("")
            canNavigateUpFolder -> viewModel.navigateUp()
        }
    }

    // User Message Toast
    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearUserMessage()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        var showFilterSheet by remember { mutableStateOf(false) }

        // View Mode Selector Chips
        val viewModeScroll = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(viewModeScroll),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExploreViewMode.entries.forEach { mode ->
                FilterChip(
                    selected = uiState.viewMode == mode,
                    onClick = {
                        haptics.click()
                        viewModel.setViewMode(mode)
                    },
                    label = { Text(mode.title, fontWeight = FontWeight.Medium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        // Search & Filter Bar (Active & visible only for non-Treemap modes)
        AnimatedVisibility(
            visible = uiState.viewMode != ExploreViewMode.TREEMAP,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Search files & folders…", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                haptics.click()
                                viewModel.setSearchQuery("")
                            }) {
                                Icon(imageVector = Icons.Rounded.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                    ),
                    singleLine = true
                )

                // Modern Filter & Sort Button
                IconButton(
                    onClick = {
                        haptics.click()
                        showFilterSheet = true
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FilterList,
                        contentDescription = "Filter and sort options",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (showFilterSheet && uiState.viewMode != ExploreViewMode.TREEMAP) {
            FilterSortBottomSheet(
                currentSort = uiState.sortOrder,
                showHiddenFiles = uiState.showHiddenFiles,
                onApply = { newSort, newHidden ->
                    viewModel.setSortOrder(newSort)
                    viewModel.setShowHiddenFiles(newHidden)
                },
                onDismiss = { showFilterSheet = false }
            )
        }

        // Breadcrumb Navigation Bar (Visible in Treemap, List, Folders modes)
        if (uiState.viewMode == ExploreViewMode.TREEMAP || uiState.viewMode == ExploreViewMode.LIST || uiState.viewMode == ExploreViewMode.FOLDERS) {
            BreadcrumbBar(
                currentPath = uiState.currentPath,
                rootPath = Environment.getExternalStorageDirectory().absolutePath,
                onNavigateToSegment = { targetPath ->
                    viewModel.navigateToDirectory(targetPath)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Bulk Selection Action Bar
        AnimatedVisibility(
            visible = uiState.isSelectionMode,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            val selectedCount = uiState.selectedPaths.size
            val currentFiles = if (uiState.searchQuery.isNotBlank()) {
                uiState.searchResults
            } else {
                directoryFiles
            }
            val selectedFiles = currentFiles.filter { uiState.selectedPaths.contains(it.path) }
            val selectedBytes = selectedFiles.sumOf { it.size }
            val isAllSelected = currentFiles.isNotEmpty() && currentFiles.all { uiState.selectedPaths.contains(it.path) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        IconButton(
                            onClick = {
                                haptics.click()
                                viewModel.clearSelection()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear Selection",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Column {
                            Text(
                                text = "$selectedCount selected",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = StorageFormatter.formatBytes(selectedBytes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = {
                                haptics.selection()
                                if (isAllSelected) viewModel.clearSelection() else viewModel.selectAll()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isAllSelected) Icons.Rounded.Deselect else Icons.Rounded.SelectAll,
                                contentDescription = if (isAllSelected) "Deselect All" else "Select All",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        FilledTonalButton(
                            onClick = {
                                haptics.warning()
                                viewModel.moveToTrashSelected(activity)
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.RestoreFromTrash,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Trash",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        Button(
                            onClick = {
                                haptics.warning()
                                viewModel.promptDeleteSelected()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Delete",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        // Main View Content
        Box(modifier = Modifier.weight(1f)) {
            val displayedFiles = if (uiState.searchQuery.isNotBlank()) {
                uiState.searchResults
            } else {
                directoryFiles
            }

            when (uiState.viewMode) {
                ExploreViewMode.TREEMAP -> {
                    TreemapCanvas(
                        nodes = treemapNodes,
                        onNodeClick = { node ->
                            haptics.click()
                            if (node.isDirectory) {
                                viewModel.navigateToDirectory(node.path)
                            } else {
                                val fileEntity = directoryFiles.find { it.path == node.path }
                                if (fileEntity != null) {
                                    viewModel.showDetails(fileEntity)
                                }
                            }
                        },
                        onSizeMeasured = { w, h ->
                            viewModel.recalculateTreemap(w, h)
                        }
                    )
                }

                ExploreViewMode.LIST -> {
                    FileListView(
                        files = displayedFiles,
                        selectedPaths = uiState.selectedPaths,
                        isSelectionMode = uiState.isSelectionMode,
                        emptyMessage = if (uiState.searchQuery.isNotBlank()) {
                            "No files matching \"${uiState.searchQuery}\""
                        } else {
                            "No files or folders in this location."
                        },
                        onItemClick = { file ->
                            if (file.isDirectory) {
                                viewModel.navigateToDirectory(file.path)
                            } else {
                                viewModel.showDetails(file)
                            }
                        },
                        onItemLongClick = { file ->
                            viewModel.toggleSelection(file.path)
                        },
                        onToggleSelect = { path ->
                            viewModel.toggleSelection(path)
                        }
                    )
                }

                ExploreViewMode.LARGEST -> {
                    val largestToDisplay = if (uiState.searchQuery.isNotBlank()) {
                        uiState.largestFiles.filter { app.quarry.tanvir.info.domain.model.SearchMatcher.matches(it.name, it.path, uiState.searchQuery) }
                    } else {
                        uiState.largestFiles
                    }
                    LargestFilesView(
                        files = largestToDisplay,
                        onFileClick = { file -> viewModel.showDetails(file) }
                    )
                }

                ExploreViewMode.TYPES -> {
                    val filesToCategorize = if (uiState.searchQuery.isNotBlank()) {
                        allCategorizedFiles.filter { app.quarry.tanvir.info.domain.model.SearchMatcher.matches(it.name, it.path, uiState.searchQuery) }
                    } else {
                        allCategorizedFiles
                    }
                    FileTypeGroupView(
                        files = filesToCategorize,
                        sortOrder = uiState.sortOrder,
                        onFileClick = { file -> viewModel.showDetails(file) }
                    )
                }

                ExploreViewMode.FOLDERS -> {
                    FolderSizeView(
                        folders = displayedFiles,
                        sortOrder = uiState.sortOrder,
                        onFolderClick = { folder -> viewModel.navigateToDirectory(folder.path) }
                    )
                }
            }
        }
    }

    // Active File Details Bottom Sheet
    uiState.activeDetailsFile?.let { file ->
        FileDetailsBottomSheet(
            file = file,
            onDismiss = { viewModel.hideDetails() },
            onOpen = { viewModel.openFile(file) },
            onShare = { viewModel.shareFile(file) },
            onRename = { viewModel.startRename(file) },
            onMoveToTrash = { viewModel.moveToTrash(activity, file) },
            onDelete = { viewModel.promptDeleteSingle(file) },
            onOpenContainingFolder = { folderPath ->
                viewModel.navigateToDirectory(folderPath)
            }
        )
    }

    // Rename Dialog
    uiState.activeRenameFile?.let { file ->
        RenameDialog(
            file = file,
            onDismiss = { viewModel.dismissRename() },
            onConfirm = { newName ->
                viewModel.executeRename(activity, file, newName)
            }
        )
    }

    // 5-Second Delete Countdown Dialog
    if (uiState.isDeleteCountdownVisible && uiState.activeDeleteCandidates.isNotEmpty()) {
        DeleteCountdownDialog(
            candidates = uiState.activeDeleteCandidates,
            isBiometricAuthRequired = uiState.isBiometricEnabled,
            onDismiss = { viewModel.dismissDeleteDialog() },
            onConfirmAuthenticatedDelete = {
                viewModel.executeAuthenticatedDelete(activity, uiState.activeDeleteCandidates)
            }
        )
    }
}
