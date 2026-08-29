package app.quarry.tanvir.info.ui.home

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.quarry.tanvir.info.R
import androidx.fragment.app.FragmentActivity
import app.quarry.tanvir.info.data.database.FileEntity
import app.quarry.tanvir.info.domain.analyzer.QuickInsight
import app.quarry.tanvir.info.domain.model.StorageCategory
import app.quarry.tanvir.info.domain.scanner.ScanState
import app.quarry.tanvir.info.ui.explore.FileDetailsBottomSheet
import app.quarry.tanvir.info.ui.explore.RenameDialog

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
    onNavigateToCategory: ((StorageCategory) -> Unit)? = null,
    onNavigateToInsight: ((QuickInsight) -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var renamingFile by remember { mutableStateOf<FileEntity?>(null) }
    var showAppManager by remember { mutableStateOf(false) }
    var appManagerStartSelection by remember { mutableStateOf(false) }
    val appManagerViewModel: AppManagerViewModel = viewModel()
    val appManagerState by appManagerViewModel.uiState.collectAsStateWithLifecycle()

    // User Message Toast
    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearUserMessage()
        }
    }

    // Refresh permission status when activity resumes
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissionState()
                if (showAppManager) {
                    appManagerViewModel.refresh()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Permission Banner if required
        if (!uiState.hasStoragePermission) {
            PermissionBanner(
                onGrantClick = {
                    viewModel.refreshPermissionState()
                }
            )
        }

        // Live Scanning Progress Card (Shown during active scan)
        AnimatedVisibility(
            visible = uiState.scanState is ScanState.Scanning,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val scanningState = uiState.scanState as? ScanState.Scanning
            if (scanningState != null) {
                ScanProgressCard(
                    progress = scanningState.progress,
                    onCancelClick = { viewModel.cancelScan() }
                )
            }
        }

        // Storage Overview Card
        StorageOverviewCard(
            overview = uiState.overview
        )

        // Analyze Storage Button (Shown when not currently scanning)
        val haptics = app.quarry.tanvir.info.domain.haptics.LocalQuarryHaptics.current
        if (uiState.scanState !is ScanState.Scanning) {
            Button(
                onClick = {
                    haptics.click()
                    viewModel.startScan()
                },
                enabled = uiState.hasStoragePermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.analyze_storage),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Quick Insights Section
        if (uiState.isQuickInsightsEnabled) {
            QuickInsightsSection(
                insights = uiState.visibleQuickInsights,
            onInsightClick = { insight ->
                if (onNavigateToInsight != null) {
                    onNavigateToInsight(insight)
                } else {
                    viewModel.selectInsight(insight)
                }
            },
                onInsightLongClick = { insight ->
                    viewModel.selectInsight(insight, startInSelectionMode = true)
                },
                hapticsEnabled = uiState.isHapticsEnabled,
                hapticStrength = uiState.hapticStrength
            )
        }

        // Storage Categories Grid (respects Miscellaneous visibility, min 4)
        CategoryGrid(
            categories = uiState.visibleCategoryBreakdown,
            onCategoryClick = { category ->
                if (category == StorageCategory.APPS) {
                    appManagerStartSelection = false
                    showAppManager = true
                } else if (onNavigateToCategory != null) {
                    onNavigateToCategory(category)
                } else {
                    viewModel.selectCategory(category)
                }
            },
            onCategoryLongClick = { category ->
                if (category == StorageCategory.APPS) {
                    appManagerStartSelection = true
                    showAppManager = true
                } else {
                    viewModel.selectCategory(category, startInSelectionMode = true)
                }
            },
            hapticsEnabled = uiState.isHapticsEnabled,
            hapticStrength = uiState.hapticStrength
        )

        Spacer(modifier = Modifier.height(8.dp))
    }

    // Category / Insight Files Detail Sheet (Stays on Home)
    uiState.activeSheetData?.let { sheetData ->
        HomeItemListBottomSheet(
            title = sheetData.title,
            category = sheetData.category,
            files = sheetData.files,
            startInSelectionMode = sheetData.startInSelectionMode,
            hapticsEnabled = uiState.isHapticsEnabled,
            hapticStrength = uiState.hapticStrength,
            onFileClick = { file ->
                viewModel.selectDetailFile(file)
            },
            onMoveToTrashSelected = { files, onComplete ->
                viewModel.moveToTrashBatch(activity, files, onComplete)
            },
            onDeleteSelected = { files, onComplete ->
                viewModel.deletePermanentlyBatch(activity, files, onComplete)
            },
            onDismiss = {
                viewModel.dismissSheet()
            }
        )
    }

    // App Manager Sheet
    if (showAppManager) {
        LaunchedEffect(showAppManager) {
            appManagerViewModel.refresh()
            if (appManagerStartSelection) {
                appManagerViewModel.setSelectionMode(true)
                appManagerStartSelection = false
            }
        }

        AppManagerBottomSheet(
            viewModel = appManagerViewModel,
            hapticsEnabled = uiState.isHapticsEnabled,
            hapticStrength = uiState.hapticStrength,
            onDismiss = {
                showAppManager = false
                appManagerViewModel.setSelectionMode(false)
            }
        )

        appManagerState.detailApp?.let { app ->
            AppDetailsBottomSheet(
                app = app,
                icon = appManagerViewModel.getIcon(app.packageName),
                onOpen = {
                    appManagerViewModel.openApp(app)
                    appManagerViewModel.dismissDetails()
                },
                onAppInfo = {
                    appManagerViewModel.openAppDetails(app)
                },
                onUninstall = {
                    appManagerViewModel.uninstall(app)
                    appManagerViewModel.dismissDetails()
                },
                onDismiss = { appManagerViewModel.dismissDetails() }
            )
        }
    }

    // Individual File Details Sheet (Stays on Home)
    uiState.selectedDetailFile?.let { file ->
        FileDetailsBottomSheet(
            file = file,
            onDismiss = { viewModel.selectDetailFile(null) },
            onOpen = { viewModel.openFile(it) },
            onShare = { viewModel.shareFile(it) },
            onRename = { renamingFile = it },
            onMoveToTrash = { viewModel.moveToTrash(activity, it) },
            onDelete = { viewModel.deleteFile(activity, it) },
            onOpenContainingFolder = {
                viewModel.selectDetailFile(null)
            }
        )
    }

    // Rename Dialog
    renamingFile?.let { file ->
        RenameDialog(
            file = file,
            onDismiss = { renamingFile = null },
            onConfirm = { newName ->
                viewModel.renameFile(activity, file, newName)
                renamingFile = null
            }
        )
    }
}
