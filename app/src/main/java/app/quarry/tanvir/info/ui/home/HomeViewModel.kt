package app.quarry.tanvir.info.ui.home

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.quarry.tanvir.info.data.database.CategoryStat
import app.quarry.tanvir.info.data.database.FileEntity
import app.quarry.tanvir.info.data.database.ScanSnapshotEntity
import app.quarry.tanvir.info.data.filesystem.FastStorageScanner
import app.quarry.tanvir.info.domain.analyzer.QuickInsight
import app.quarry.tanvir.info.domain.analyzer.StorageAnalyzer
import app.quarry.tanvir.info.domain.analyzer.StorageOverviewData
import app.quarry.tanvir.info.domain.file.FileOperationsManager
import app.quarry.tanvir.info.domain.model.StorageCategory
import app.quarry.tanvir.info.domain.scanner.ScanRepository
import app.quarry.tanvir.info.domain.scanner.ScanState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeSheetData(
    val title: String,
    val category: StorageCategory,
    val files: List<FileEntity>
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
    private val fileOperationsManager = FileOperationsManager(application, repository)
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

    fun startScan() {
        repository.startScan()
    }

    fun cancelScan() {
        repository.cancelScan()
    }

    fun refreshPermissionState() {
        _permissionState.value = checkHasStoragePermission()
    }

    fun selectCategory(category: StorageCategory) {
        activeSheetCollectJob?.cancel()
        activeSheetCollectJob = viewModelScope.launch(Dispatchers.IO) {
            repository.getFilesByCategory(category.name).collect { files ->
                _activeSheetData.value = HomeSheetData(
                    title = category.displayName,
                    category = category,
                    files = files
                )
            }
        }
    }

    fun selectInsight(insight: QuickInsight) {
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
                    files = files
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

    fun renameFile(file: FileEntity, newName: String) {
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

    fun deleteFile(file: FileEntity) {
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

    fun clearUserMessage() {
        _userMessage.value = null
    }
}
