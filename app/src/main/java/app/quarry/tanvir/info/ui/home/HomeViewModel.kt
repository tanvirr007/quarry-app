package app.quarry.tanvir.info.ui.home

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.quarry.tanvir.info.data.database.CategoryStat
import app.quarry.tanvir.info.data.database.FileEntity
import app.quarry.tanvir.info.data.database.ScanSnapshotEntity
import app.quarry.tanvir.info.data.filesystem.FastStorageScanner
import app.quarry.tanvir.info.domain.analyzer.StorageAnalyzer
import app.quarry.tanvir.info.domain.analyzer.StorageOverviewData
import app.quarry.tanvir.info.domain.scanner.ScanRepository
import app.quarry.tanvir.info.domain.scanner.ScanState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val overview: StorageOverviewData = StorageOverviewData(),
    val scanState: ScanState = ScanState.Idle,
    val hasStoragePermission: Boolean = true,
    val isInitialLoading: Boolean = false
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ScanRepository.getInstance(application)
    private val _permissionState = MutableStateFlow(checkHasStoragePermission())

    val uiState: StateFlow<HomeUiState> = combine(
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

        HomeUiState(
            overview = overview,
            scanState = scanState,
            hasStoragePermission = hasPermission,
            isInitialLoading = false
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
}
