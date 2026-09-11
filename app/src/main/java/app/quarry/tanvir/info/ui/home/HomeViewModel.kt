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
import app.quarry.tanvir.info.domain.volume.StorageVolumeInfo
import app.quarry.tanvir.info.domain.volume.StorageVolumeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

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
    val hapticStrength: Int = 60,
    val availableVolumes: List<StorageVolumeInfo> = emptyList(),
    val selectedVolume: StorageVolumeInfo? = null
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ScanRepository.getInstance(application)
    private val prefsRepo = UserPreferencesRepository.getInstance(application)
    private val volumeManager = StorageVolumeManager(application)
    private val fileOperationsManager = FileOperationsManager(application, repository)
    private val securityManager = BiometricSecurityManager(application)
    private val appManager = AppManager(application)
    private val _permissionState = MutableStateFlow(checkHasStoragePermission())
    private val _availableVolumes = MutableStateFlow<List<StorageVolumeInfo>>(emptyList())
    private val _selectedVolume = MutableStateFlow<StorageVolumeInfo?>(null)
    private val _activeSheetData = MutableStateFlow<HomeSheetData?>(null)
    private val _selectedDetailFile = MutableStateFlow<FileEntity?>(null)
    private val _userMessage = MutableStateFlow<String?>(null)
    private val _appsSize = MutableStateFlow(0L)
    private val _appsCount = MutableStateFlow(0L)
    private var activeSheetCollectJob: Job? = null
    private var appsLoadJob: Job? = null

    private data class VolumeScannedData(
        val categoryStats: List<CategoryStat> = emptyList(),
        val largeFiles: List<FileEntity> = emptyList(),
        val apks: List<FileEntity> = emptyList(),
        val screenshots: List<FileEntity> = emptyList()
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val volumeScannedData: Flow<VolumeScannedData> = _selectedVolume.flatMapLatest { vol ->
        if (vol == null) {
            flowOf(VolumeScannedData())
        } else {
            combine(
                repository.getCategoryStats(vol.id),
                repository.getLargeFiles(minSizeBytes = 50 * 1024 * 1024L, volumeId = vol.id),
                repository.getApkFiles(vol.id),
                repository.getScreenshots(vol.id)
            ) { stats, large, apks, screenshots ->
                VolumeScannedData(stats, large, apks, screenshots)
            }
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        combine(
            repository.scanState,
            volumeScannedData,
            repository.allSnapshots,
            _permissionState,
            _appsSize,
            _appsCount,
            prefsRepo.isQuickInsightsEnabled,
            prefsRepo.enabledCategories,
            prefsRepo.isHapticsEnabled,
            prefsRepo.hapticStrength,
            _availableVolumes,
            _selectedVolume
        ) { args ->
            val scanState = args[0] as ScanState
            val scannedData = args[1] as VolumeScannedData
            @Suppress("UNCHECKED_CAST")
            val snapshots = args[2] as List<ScanSnapshotEntity>
            val hasPermission = args[3] as Boolean
            val appsSize = args[4] as Long
            val appsCount = args[5] as Long
            val quickInsightsEnabled = args[6] as Boolean
            @Suppress("UNCHECKED_CAST")
            val enabledCats = args[7] as Set<String>
            val hapticsEnabled = args[8] as Boolean
            val hapticStrength = args[9] as Int
            @Suppress("UNCHECKED_CAST")
            val availableVols = args[10] as List<StorageVolumeInfo>
            val currentVol = args[11] as? StorageVolumeInfo

            val rootDir = if (currentVol != null) File(currentVol.path) else Environment.getExternalStorageDirectory()
            val totalBytes = if (currentVol != null && currentVol.totalBytes > 0) currentVol.totalBytes else FastStorageScanner.getTotalStorageBytes(rootDir)
            val freeBytes = if (currentVol != null && currentVol.totalBytes > 0) currentVol.freeBytes else FastStorageScanner.getFreeStorageBytes(rootDir)

            val largeFilesSize = scannedData.largeFiles.sumOf { it.size }
            val apksSize = scannedData.apks.sumOf { it.size }
            val screenshotsSize = scannedData.screenshots.sumOf { it.size }
            val isPrimary = currentVol?.isPrimary ?: true
            val effectiveAppsSize = if (isPrimary) appsSize else 0L
            val effectiveAppsCount = if (isPrimary) appsCount else 0L

            val overview = StorageAnalyzer.calculateOverview(
                volumeName = currentVol?.name ?: "Internal Storage",
                volumePath = currentVol?.path ?: rootDir.absolutePath,
                totalBytes = totalBytes,
                freeBytes = freeBytes,
                categoryStats = scannedData.categoryStats,
                snapshots = snapshots,
                isPrimary = isPrimary,
                largeFilesSize = largeFilesSize,
                largeFilesCount = scannedData.largeFiles.size.toLong(),
                apksSize = apksSize,
                apksCount = scannedData.apks.size.toLong(),
                screenshotsSize = screenshotsSize,
                screenshotsCount = scannedData.screenshots.size.toLong(),
                appsSize = effectiveAppsSize,
                appsCount = effectiveAppsCount
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

            OverviewWithVisibility(
                overview = overview,
                visibleCategories = visibleCategories,
                visibleInsights = visibleInsights,
                scanState = scanState,
                hasPermission = hasPermission,
                isQuickInsightsEnabled = quickInsightsEnabled,
                enabledCategories = effectiveEnabled,
                isHapticsEnabled = hapticsEnabled,
                hapticStrength = hapticStrength,
                availableVolumes = availableVols,
                selectedVolume = currentVol
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
            hapticStrength = vis.hapticStrength,
            availableVolumes = vis.availableVolumes,
            selectedVolume = vis.selectedVolume
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
        val hapticStrength: Int,
        val availableVolumes: List<StorageVolumeInfo> = emptyList(),
        val selectedVolume: StorageVolumeInfo? = null
    )

    init {
        refreshVolumes()
        checkAndTriggerInitialScan()
        loadAppsInfo()
    }

    fun refreshVolumes() {
        val detected = volumeManager.getDetectedVolumes()
        _availableVolumes.value = detected
        viewModelScope.launch {
            val savedId = try { prefsRepo.selectedVolumeId.first() } catch (_: Exception) { "internal_storage" }
            val match = detected.find { it.id == savedId } ?: detected.find { it.isPrimary } ?: detected.firstOrNull()
            if (match != null) {
                _selectedVolume.value = match
                if (match.id != savedId) {
                    prefsRepo.setSelectedVolumeId(match.id)
                }
            }
        }
    }

    fun selectVolume(volume: StorageVolumeInfo) {
        viewModelScope.launch {
            _selectedVolume.value = volume
            prefsRepo.setSelectedVolumeId(volume.id)
        }
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
        val currentVol = _selectedVolume.value
        val rootDir = if (currentVol != null) File(currentVol.path) else Environment.getExternalStorageDirectory()
        val volId = currentVol?.id ?: "internal_storage"
        val volName = currentVol?.name ?: "Internal Storage"
        repository.startScan(
            rootDirectory = rootDir,
            volumeId = volId,
            volumeName = volName
        )
    }

    fun cancelScan() {
        repository.cancelScan()
    }

    fun refreshPermissionState() {
        val hasPermission = checkHasStoragePermission()
        val previous = _permissionState.value
        _permissionState.value = hasPermission
        refreshVolumes()
        if (hasPermission && (!previous || repository.scanState.value is ScanState.Idle)) {
            checkAndTriggerInitialScan()
        }
        loadAppsInfo()
    }

    private fun checkAndTriggerInitialScan() {
        if (!checkHasStoragePermission()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val volId = _selectedVolume.value?.id ?: "internal_storage"
                val totalFiles = repository.getTotalFileCount(volId).first()
                if (totalFiles == 0L && repository.scanState.value is ScanState.Idle) {
                    startScan()
                }
            } catch (e: Exception) {
                // Ignore failure during initial check
            }
        }
    }

    fun selectCategory(category: StorageCategory, startInSelectionMode: Boolean = false) {
        activeSheetCollectJob?.cancel()
        val volId = _selectedVolume.value?.id ?: "internal_storage"
        activeSheetCollectJob = viewModelScope.launch(Dispatchers.IO) {
            repository.getFilesByCategory(category.name, volId).collect { files ->
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
        val volId = _selectedVolume.value?.id ?: "internal_storage"
        activeSheetCollectJob = viewModelScope.launch(Dispatchers.IO) {
            val flow = when (insight.id) {
                "large_files" -> repository.getLargeFiles(minSizeBytes = 50 * 1024 * 1024L, volumeId = volId)
                "apks" -> repository.getApkFiles(volId)
                "screenshots" -> repository.getScreenshots(volId)
                else -> repository.getFilesByCategory(insight.category.name, volId)
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
