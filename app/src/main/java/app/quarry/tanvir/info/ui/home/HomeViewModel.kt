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
import kotlinx.coroutines.launch

data class HomeSheetData(
    val title: String,
    val category: StorageCategory,
    val files: List<FileEntity>,
    val startInSelectionMode: Boolean = false
)

data class HomeUiState(
    val overview: StorageOverviewData = StorageOverviewData(),
    val scanState: ScanState = ScanState.Idle,
    val hasStoragePermission: Boolean = true,
    val isInitialLoading: Boolean = false,
    val activeSheetData: HomeSheetData? = null,
    val selectedDetailFile: FileEntity? = null,
    val userMessage: String? = null
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ScanRepository.getInstance(application)
    private val prefsRepo = UserPreferencesRepository.getInstance(application)
    private val fileOperationsManager = FileOperationsManager(application, repository)
    private val securityManager = BiometricSecurityManager(application)
    private val _permissionState = MutableStateFlow(checkHasStoragePermission())
    private val _activeSheetData = MutableStateFlow<HomeSheetData?>(null)
    private val _selectedDetailFile = MutableStateFlow<FileEntity?>(null)
    private val _userMessage = MutableStateFlow<String?>(null)
    private var activeSheetCollectJob: Job? = null

    val uiState: StateFlow<HomeUiState> = combine(
        combine(
            repository.scanState,
            repository.categoryStats,
            repository.allSnapshots,
            repository.getLargeFiles(),
            repository.getApkFiles(),
            repository.getScreenshots(),
            _permissionState
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
                screenshotsCount = screenshots.size.toLong()
            )

            Triple(overview, scanState, hasPermission)
        },
        _activeSheetData,
        _selectedDetailFile,
        _userMessage
    ) { (overview, scanState, hasPermission), activeSheet, detailFile, message ->
        HomeUiState(
            overview = overview,
            scanState = scanState,
            hasStoragePermission = hasPermission,
            isInitialLoading = false,
            activeSheetData = activeSheet,
            selectedDetailFile = detailFile,
            userMessage = message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    init {
        checkAndTriggerInitialScan()
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
                _userMessage.value = "Moved $successCount items to Trash"
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
                    _userMessage.value = "File deleted"
                    _selectedDetailFile.value = null
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
                _userMessage.value = "Deleted $successCount files permanently"
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
