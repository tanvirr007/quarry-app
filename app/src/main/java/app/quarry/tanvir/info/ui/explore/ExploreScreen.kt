package app.quarry.tanvir.info.ui.explore

import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
    val treemapNodes by viewModel.treemapLayoutNodes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    // Handle Android Back gesture to navigate up folder hierarchy
    BackHandler(enabled = uiState.currentPath != Environment.getExternalStorageDirectory().absolutePath) {
        viewModel.navigateUp()
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
        // Search & View Mode Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search files & folders…") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search"
                    )
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(imageVector = Icons.Rounded.Close, contentDescription = "Clear search")
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )

            // Sort & Filter Dropdown
            var showSortMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(
                    onClick = { showSortMenu = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FilterList,
                        contentDescription = "Sort options",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    FileSortOrder.entries.forEach { sortOption ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = sortOption.displayName,
                                    fontWeight = if (uiState.sortOrder == sortOption) FontWeight.Bold else FontWeight.Normal,
                                    color = if (uiState.sortOrder == sortOption) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                viewModel.setSortOrder(sortOption)
                                showSortMenu = false
                            }
                        )
                    }
                }
            }
        }

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
                    onClick = { viewModel.setViewMode(mode) },
                    label = { Text(mode.title, fontWeight = FontWeight.Medium) }
                )
            }
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
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val selectedCount = uiState.selectedPaths.size
            val selectedFiles = directoryFiles.filter { uiState.selectedPaths.contains(it.path) }
            val selectedBytes = selectedFiles.sumOf { it.size }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "$selectedCount selected",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = StorageFormatter.formatBytes(selectedBytes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(onClick = { viewModel.selectAll() }) {
                            Icon(imageVector = Icons.Rounded.SelectAll, contentDescription = "Select All")
                        }
                        Button(
                            onClick = { viewModel.promptDeleteSelected() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete")
                        }
                        TextButton(onClick = { viewModel.clearSelection() }) {
                            Text("Cancel")
                        }
                    }
                }
            }
        }

        // Main View Content
        Box(modifier = Modifier.weight(1f)) {
            val displayedFiles = if (uiState.searchQuery.isNotEmpty()) {
                directoryFiles.filter { it.name.contains(uiState.searchQuery, ignoreCase = true) }
            } else {
                directoryFiles
            }

            when (uiState.viewMode) {
                ExploreViewMode.TREEMAP -> {
                    TreemapCanvas(
                        nodes = treemapNodes,
                        onNodeClick = { node ->
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
                    LargestFilesView(
                        files = uiState.largestFiles,
                        onFileClick = { file -> viewModel.showDetails(file) }
                    )
                }

                ExploreViewMode.TYPES -> {
                    FileTypeGroupView(
                        files = displayedFiles,
                        onFileClick = { file -> viewModel.showDetails(file) }
                    )
                }

                ExploreViewMode.FOLDERS -> {
                    FolderSizeView(
                        folders = displayedFiles,
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
                activity?.let {
                    viewModel.executeRename(it, file, newName)
                }
            }
        )
    }

    // 5-Second Delete Countdown Dialog
    if (uiState.isDeleteCountdownVisible && uiState.activeDeleteCandidates.isNotEmpty()) {
        DeleteCountdownDialog(
            candidates = uiState.activeDeleteCandidates,
            onDismiss = { viewModel.dismissDeleteDialog() },
            onConfirmAuthenticatedDelete = {
                activity?.let {
                    viewModel.executeAuthenticatedDelete(it, uiState.activeDeleteCandidates)
                }
            }
        )
    }
}
