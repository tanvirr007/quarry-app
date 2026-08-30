package app.quarry.tanvir.info.ui.home

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.fragment.app.FragmentActivity
import app.quarry.tanvir.info.data.database.CategoryStat
import app.quarry.tanvir.info.data.database.FileEntity
import app.quarry.tanvir.info.data.database.ScanSnapshotEntity
import app.quarry.tanvir.info.data.filesystem.FastStorageScanner
import app.quarry.tanvir.info.data.preferences.UserPreferencesRepository
import app.quarry.tanvir.info.domain.analyzer.QuickInsight
import app.quarry.tanvir.info.domain.app.AppManager
import app.quarry.tanvir.info.domain.analyzer.StorageAnalyzer
import app.quarry.tanvir.info.domain.analyzer.StorageOverviewData
import app.quarry.tanvir.info.domain.file.FileOperationsManager
import app.quarry.tanvir.info.domain.model.StorageCategory
import app.quarry.tanvir.info.domain.scanner.ScanRepository
import app.quarry.tanvir.info.domain.scanner.ScanState
import app.quarry.tanvir.info.domain.security.BiometricSecurityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeSheetData(
    val title: String,
    val category: StorageCategory,
    val files: List<FileEntity>,
    val startInSelectionMode: Boolean = false
)

data class HomeUiState(
    val overview: StorageOverviewData = StorageOverviewData(),
    val visibleCategoryBreakdown: List<app.quarry.tanvir.info.domain.analyzer.StorageCategoryData> = emptyList(),
    val visibleQuickInsights: List<QuickInsight> = emptyList(),
    val scanState: ScanState = ScanState.Idle,
    val hasStoragePermission: Boolean = true,
    val isInitialLoading: Boolean = false,
    val activeSheetData: HomeSheetData? = null,
    val selectedDetailFile: FileEntity? = null,
    val userMessage: String? = null,
    val isQuickInsightsEnabled: Boolean = true,
    val enabledCategories: Set<String> = emptySet(),
    val isHapticsEnabled: Boolean = true,
    val hapticStrength: Int = 60
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ScanRepository.getInstance(application)
    private val prefsRepo = UserPreferencesRepository.getInstance(application)
    private val fileOperationsManager = FileOperationsManager(application, repository)
    private val securityManager = BiometricSecurityManager(application)
    private val appManager = AppManager(application)
    private val _permissionState = MutableStateFlow(checkHasStoragePermission())
    private val _activeSheetData = MutableStateFlow<HomeSheetData?>(null)
    private val _selectedDetailFile = MutableStateFlow<FileEntity?>(null)
    private val _userMessage = MutableStateFlow<String?>(null)
    private val _appsSize = MutableStateFlow(0L)
    private val _appsCount = MutableStateFlow(0L)
    private var activeSheetCollectJob: Job? = null
    private var appsLoadJob: Job? = null

    val uiState: StateFlow<HomeUiState> = combine(
        combine(
            repository.scanState,
            repository.categoryStats,
            repository.allSnapshots,
            repository.getLargeFiles(),
            repository.getApkFiles(),
            repository.getScreenshots(),
            _permissionState,
            _appsSize,
            _appsCount,
            prefsRepo.isQuickInsightsEnabled,
            prefsRepo.enabledCategories,
            prefsRepo.isHapticsEnabled,
            prefsRepo.hapticStrength
        ) { args ->
            @Suppress("UNCHECKED_CAST")
            val scanState = args[0] as ScanState
            @Suppress("UNCHECKED_CAST")
            val categoryStats = args[1] as List<CategoryStat>
            @Suppress("UNCHECKED_CAST")
            val snapshots = args[2] as List<ScanSnapshotEntity>
            @Suppress("UNCHECKED_CAST")
            val largeFiles = args[3] as List<FileEntity>
            @Suppress("UNCHECKED_CAST")
            val apks = args[4] as List<FileEntity>
            @Suppress("UNCHECKED_CAST")
            val screenshots = args[5] as List<FileEntity>
            val hasPermission = args[6] as Boolean
            val appsSize = args[7] as Long
            val appsCount = args[8] as Long
            val quickInsightsEnabled = args[9] as Boolean
            @Suppress("UNCHECKED_CAST")
            val enabledCats = args[10] as Set<String>
            val hapticsEnabled = args[11] as Boolean
            val hapticStrength = args[12] as Int

            val rootDir = Environment.getExternalStorageDirectory()
            val totalBytes = FastStorageScanner.getTotalStorageBytes(rootDir)
            val freeBytes = FastStorageScanner.getFreeStorageBytes(rootDir)

            val largeFilesSize = largeFiles.sumOf { it.size }
            val apksSize = apks.sumOf { it.size }
            val screenshotsSize = screenshots.sumOf { it.size }

            val overview = StorageAnalyzer.calculateOverview(
                volumeName = "Internal Storage",
                volumePath = rootDir.absolutePath,
                totalBytes = totalBytes,
                freeBytes = freeBytes,
                categoryStats = categoryStats,
                snapshots = snapshots,
                largeFilesSize = largeFilesSize,
                largeFilesCount = largeFiles.size.toLong(),
                apksSize = apksSize,
                apksCount = apks.size.toLong(),
                screenshotsSize = screenshotsSize,
                screenshotsCount = screenshots.size.toLong(),
                appsSize = appsSize,
                appsCount = appsCount
            )

            val allCats = StorageCategory.entries.map { it.name }.toSet()
            val effectiveEnabled = if (enabledCats.isEmpty()) allCats else enabledCats
            val visibleCategories = if (effectiveEnabled.size < 4) {
                overview.categoryBreakdown
            } else {
                val filtered = overview.categoryBreakdown.filter { effectiveEnabled.contains(it.category.name) }
                if (filtered.size < 4) overview.categoryBreakdown else filtered
            }
            val visibleInsights = if (quickInsightsEnabled) overview.quickInsights else emptyList()
            // Pack: overview + visibles + flags in a single object via 7-tuple map not ideal, use custom holder
            OverviewWithVisibility(
                overview = overview,
                visibleCategories = visibleCategories,
                visibleInsights = visibleInsights,
                scanState = scanState,
                hasPermission = hasPermission,
                isQuickInsightsEnabled = quickInsightsEnabled,
                enabledCategories = effectiveEnabled,
                isHapticsEnabled = hapticsEnabled,
                hapticStrength = hapticStrength
            )
        },
        _activeSheetData,
        _selectedDetailFile,
        _userMessage
    ) { vis, activeSheet, detailFile, message ->
        HomeUiState(
            overview = vis.overview,
            visibleCategoryBreakdown = vis.visibleCategories,
            visibleQuickInsights = vis.visibleInsights,
            scanState = vis.scanState,
            hasStoragePermission = vis.hasPermission,
            isInitialLoading = false,
            activeSheetData = activeSheet,
            selectedDetailFile = detailFile,
            userMessage = message,
            isQuickInsightsEnabled = vis.isQuickInsightsEnabled,
            enabledCategories = vis.enabledCategories,
            isHapticsEnabled = vis.isHapticsEnabled,
            hapticStrength = vis.hapticStrength
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    private data class OverviewWithVisibility(
        val overview: StorageOverviewData,
        val visibleCategories: List<app.quarry.tanvir.info.domain.analyzer.StorageCategoryData>,
        val visibleInsights: List<QuickInsight>,
        val scanState: ScanState,
        val hasPermission: Boolean,
        val isQuickInsightsEnabled: Boolean,
        val enabledCategories: Set<String>,
        val isHapticsEnabled: Boolean,
        val hapticStrength: Int
    )

    init {
        checkAndTriggerInitialScan()
        loadAppsInfo()
    }

    private fun loadAppsInfo() {
        appsLoadJob?.cancel()
        appsLoadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val installedApps = appManager.getInstalledApps()
                _appsSize.value = installedApps.sumOf { it.size.totalBytes }
                _appsCount.value = installedApps.size.toLong()
            } catch (e: Exception) {
                // Apps info is optional; leave at zero on failure.
            }
        }
    }

    fun refreshAppsInfo() {
        loadAppsInfo()
    }

    fun startScan() {
        if (!checkHasStoragePermission()) {
            _userMessage.value = "Storage permission is required to analyze files"
            return
        }
        repository.startScan()
    }

    fun cancelScan() {
        repository.cancelScan()
    }

    fun refreshPermissionState() {
        val hasPermission = checkHasStoragePermission()
        val previous = _permissionState.value
        _permissionState.value = hasPermission
        if (hasPermission && (!previous || repository.scanState.value is ScanState.Idle)) {
            checkAndTriggerInitialScan()
        }
        loadAppsInfo()
    }

    private fun checkAndTriggerInitialScan() {
        if (!checkHasStoragePermission()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val totalFiles = repository.totalFiles.first()
                if (totalFiles == 0L && repository.scanState.value is ScanState.Idle) {
                    repository.startScan()
                }
            } catch (e: Exception) {
                // Ignore failure during initial check
            }
        }
    }

    fun selectCategory(category: StorageCategory, startInSelectionMode: Boolean = false) {
        activeSheetCollectJob?.cancel()
        activeSheetCollectJob = viewModelScope.launch(Dispatchers.IO) {
            repository.getFilesByCategory(category.name).collect { files ->
                _activeSheetData.value = HomeSheetData(
                    title = category.displayName,
                    category = category,
                    files = files,
                    startInSelectionMode = startInSelectionMode
                )
            }
        }
    }

    fun selectInsight(insight: QuickInsight, startInSelectionMode: Boolean = false) {
        activeSheetCollectJob?.cancel()
        activeSheetCollectJob = viewModelScope.launch(Dispatchers.IO) {
            val flow = when (insight.id) {
                "large_files" -> repository.getLargeFiles()
                "apks" -> repository.getApkFiles()
                "screenshots" -> repository.getScreenshots()
                else -> repository.getFilesByCategory(insight.category.name)
            }
            flow.collect { files ->
                _activeSheetData.value = HomeSheetData(
                    title = insight.title,
                    category = insight.category,
                    files = files,
                    startInSelectionMode = startInSelectionMode
                )
            }
        }
    }

    fun dismissSheet() {
        activeSheetCollectJob?.cancel()
        _activeSheetData.value = null
    }

    fun selectDetailFile(file: FileEntity?) {
        _selectedDetailFile.value = file
    }

    fun openFile(file: FileEntity) {
        fileOperationsManager.openFile(file.path)
    }

    fun shareFile(file: FileEntity) {
        fileOperationsManager.shareFile(file.path)
    }

    fun renameFile(activity: FragmentActivity?, file: FileEntity, newName: String) {
        val performRename: () -> Unit = {
            viewModelScope.launch(Dispatchers.IO) {
                val result = fileOperationsManager.renameFile(file.path, newName)
                if (result.isSuccess) {
                    _userMessage.value = "Renamed successfully"
                    _selectedDetailFile.value = null
                } else {
                    _userMessage.value = "Rename failed: ${result.exceptionOrNull()?.message}"
                }
            }
        }

        viewModelScope.launch {
            val isAuthEnabled = prefsRepo.isBiometricAuthEnabled.first()
            if (!isAuthEnabled) {
                performRename()
            } else {
                if (activity == null) {
                    _userMessage.value = "Unable to start authentication"
                    return@launch
                }
                securityManager.authenticate(
                    activity = activity,
                    title = "Confirm File Rename",
                    subtitle = "Authenticate to rename ${file.name}",
                    onSuccess = performRename,
                    onError = { error ->
                        _userMessage.value = error
                    }
                )
            }
        }
    }

    fun moveToTrash(activity: FragmentActivity?, file: FileEntity) {
        val performMoveToTrash: () -> Unit = {
            viewModelScope.launch(Dispatchers.IO) {
                val result = fileOperationsManager.moveToTrash(file.path)
                if (result.isSuccess) {
                    _userMessage.value = "Moved \"${file.name}\" to Trash"
                    _selectedDetailFile.value = null
                } else {
                    _userMessage.value = "Failed to move to Trash: ${result.exceptionOrNull()?.message}"
                }
            }
        }

        viewModelScope.launch {
            val isAuthEnabled = prefsRepo.isBiometricAuthEnabled.first()
            if (!isAuthEnabled) {
                performMoveToTrash()
            } else {
                if (activity == null) {
                    _userMessage.value = "Unable to start authentication"
                    return@launch
                }
                securityManager.authenticate(
                    activity = activity,
                    title = "Confirm Move to Trash",
                    subtitle = "Authenticate to move ${file.name} to Trash",
                    onSuccess = performMoveToTrash,
                    onError = { error ->
                        _userMessage.value = error
                    }
                )
            }
        }
    }

    fun moveToTrashBatch(activity: FragmentActivity?, files: List<FileEntity>, onComplete: () -> Unit = {}) {
        val paths = files.map { it.path }
        if (paths.isEmpty()) return

        val performBulkMoveToTrash: () -> Unit = {
            viewModelScope.launch(Dispatchers.IO) {
                val results = fileOperationsManager.bulkMoveToTrash(paths)
                val successCount = results.count { it.value }
                val trashedPaths = results.filter { it.value }.keys
                _userMessage.value = "Moved $successCount items to Trash"
                _activeSheetData.update { current ->
                    if (current == null) null
                    else {
                        val remaining = current.files.filterNot { trashedPaths.contains(it.path) }
                        if (remaining.isEmpty()) null
                        else current.copy(files = remaining)
                    }
                }
                onComplete()
            }
        }

        viewModelScope.launch {
            val isAuthEnabled = prefsRepo.isBiometricAuthEnabled.first()
            if (!isAuthEnabled) {
                performBulkMoveToTrash()
            } else {
                if (activity == null) {
                    _userMessage.value = "Unable to start authentication"
                    return@launch
                }
                securityManager.authenticate(
                    activity = activity,
                    title = "Confirm Move to Trash",
                    subtitle = "Authenticate to move ${paths.size} files to Trash",
                    onSuccess = performBulkMoveToTrash,
                    onError = { error ->
                        _userMessage.value = error
                    }
                )
            }
        }
    }

    fun deleteFile(activity: FragmentActivity?, file: FileEntity) {
        val performDelete: () -> Unit = {
            viewModelScope.launch(Dispatchers.IO) {
                val result = fileOperationsManager.deletePermanently(file.path)
                if (result.isSuccess) {
                    _userMessage.value = "Permanently deleted \"${file.name}\""
                    _selectedDetailFile.value = null
                    _activeSheetData.update { current ->
                        if (current == null) null
                        else {
                            val remaining = current.files.filterNot { it.path == file.path }
                            if (remaining.isEmpty()) null
                            else current.copy(files = remaining)
                        }
                    }
                } else {
                    _userMessage.value = "Delete failed: ${result.exceptionOrNull()?.message}"
                }
            }
        }

        viewModelScope.launch {
            val isAuthEnabled = prefsRepo.isBiometricAuthEnabled.first()
            if (!isAuthEnabled) {
                performDelete()
            } else {
                if (activity == null) {
                    _userMessage.value = "Unable to start authentication"
                    return@launch
                }
                securityManager.authenticate(
                    activity = activity,
                    title = "Confirm File Deletion",
                    subtitle = "Authenticate to delete ${file.name}",
                    onSuccess = performDelete,
                    onError = { error ->
                        _userMessage.value = error
                    }
                )
            }
        }
    }

    fun deletePermanentlyBatch(activity: FragmentActivity?, files: List<FileEntity>, onComplete: () -> Unit = {}) {
        val paths = files.map { it.path }
        if (paths.isEmpty()) return

        val performBulkDelete: () -> Unit = {
            viewModelScope.launch(Dispatchers.IO) {
                val results = fileOperationsManager.bulkDelete(paths)
                val successCount = results.count { it.value }
                val deletedPaths = results.filter { it.value }.keys
                _userMessage.value = "Deleted $successCount files permanently"
                _activeSheetData.update { current ->
                    if (current == null) null
                    else {
                        val remaining = current.files.filterNot { deletedPaths.contains(it.path) }
                        if (remaining.isEmpty()) null
                        else current.copy(files = remaining)
                    }
                }
                onComplete()
            }
        }

        viewModelScope.launch {
            val isAuthEnabled = prefsRepo.isBiometricAuthEnabled.first()
            if (!isAuthEnabled) {
                performBulkDelete()
            } else {
                if (activity == null) {
                    _userMessage.value = "Unable to start authentication"
                    return@launch
                }
                val title = if (files.size == 1) "Confirm Deletion" else "Confirm Bulk Deletion"
                val subtitle = if (files.size == 1) "Authenticate to delete ${files[0].name}" else "Authenticate to delete ${files.size} files"

                securityManager.authenticate(
                    activity = activity,
                    title = title,
                    subtitle = subtitle,
                    onSuccess = performBulkDelete,
                    onError = { error ->
                        _userMessage.value = error
                    }
                )
            }
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }
}
