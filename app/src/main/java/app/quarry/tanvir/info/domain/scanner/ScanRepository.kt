package app.quarry.tanvir.info.domain.scanner

import android.content.Context
import android.os.Environment
import app.quarry.tanvir.info.data.database.CategoryStat
import app.quarry.tanvir.info.data.database.FileEntity
import app.quarry.tanvir.info.data.database.QuarryDatabase
import app.quarry.tanvir.info.data.database.ScanSnapshotEntity
import app.quarry.tanvir.info.data.filesystem.FastStorageScanner
import app.quarry.tanvir.info.data.filesystem.ScanProgressUpdate
import app.quarry.tanvir.info.domain.model.StorageCategory
import app.quarry.tanvir.info.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ScanRepository(
    private val database: QuarryDatabase,
    private val scanner: FastStorageScanner = FastStorageScanner(),
    private val userPreferences: UserPreferencesRepository? = null
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var scanJob: Job? = null

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    // Room Flows
    val categoryStats: Flow<List<CategoryStat>> = database.fileDao().getCategoryStats()
    val totalFiles: Flow<Long> = database.fileDao().getTotalFileCount()
    val totalScannedBytes: Flow<Long?> = database.fileDao().getTotalScannedBytes()
    val latestSnapshot: Flow<ScanSnapshotEntity?> = database.scanSnapshotDao().getLatestSnapshot()
    val allSnapshots: Flow<List<ScanSnapshotEntity>> = database.scanSnapshotDao().getAllSnapshots()

    fun getAllFiles(): Flow<List<FileEntity>> = database.fileDao().getAllFiles()
    suspend fun getAllFilesSync(): List<FileEntity> = database.fileDao().getAllFilesSync()
    fun getAllNonDirectoryFiles(): Flow<List<FileEntity>> = database.fileDao().getAllNonDirectoryFiles()
    suspend fun getAllNonDirectoryFilesSync(): List<FileEntity> = database.fileDao().getAllNonDirectoryFilesSync()
    fun getChildren(parentPath: String): Flow<List<FileEntity>> = database.fileDao().getChildren(parentPath)
    fun getFilesByCategory(category: String): Flow<List<FileEntity>> = database.fileDao().getFilesByCategory(category)
    fun getLargestFiles(limit: Int = 100): Flow<List<FileEntity>> = database.fileDao().getLargestFiles(limit)
    fun searchFiles(query: String): Flow<List<FileEntity>> = database.fileDao().searchFiles(query)
    fun getScreenshots(): Flow<List<FileEntity>> = database.fileDao().getScreenshots()
    fun getDownloads(): Flow<List<FileEntity>> = database.fileDao().getDownloads()
    fun getApkFiles(): Flow<List<FileEntity>> = database.fileDao().getApkFiles()
    fun getLargeFiles(minSizeBytes: Long = 50 * 1024 * 1024L): Flow<List<FileEntity>> = database.fileDao().getLargeFiles(minSizeBytes)
    fun getOldFiles(beforeTimestamp: Long): Flow<List<FileEntity>> = database.fileDao().getOldFiles(beforeTimestamp)
    fun getEmptyFolders(): Flow<List<FileEntity>> = database.fileDao().getEmptyFolders()

    fun startScan(rootDirectory: File = Environment.getExternalStorageDirectory()) {
        if (_scanState.value is ScanState.Scanning) return

        scanJob?.cancel()
        scanJob = repositoryScope.launch {
            try {
                _scanState.value = ScanState.Scanning(
                    ScanProgress(currentPath = rootDirectory.absolutePath)
                )

                val estimatedTotalBytes = FastStorageScanner.getUsedStorageBytes(rootDirectory)
                val scanHidden = try { userPreferences?.scanHiddenFiles?.first() ?: false } catch (e: Exception) { false }
                val excluded = try { userPreferences?.excludedFolders?.first() ?: emptySet() } catch (e: Exception) { emptySet() }

                scanner.scanStorage(
                    rootDirectory = rootDirectory,
                    estimatedTotalBytes = estimatedTotalBytes,
                    includeHiddenFiles = scanHidden,
                    excludedPaths = excluded
                ).collect { update ->
                    when (update) {
                        is ScanProgressUpdate.Progress -> {
                            _scanState.value = ScanState.Scanning(update.progress)
                        }
                        is ScanProgressUpdate.Finished -> {
                            // Persist to Room
                            database.fileDao().replaceAllFiles(update.result.files)

                            // Save Snapshot
                            val stats = database.fileDao().getCategoryStatsSync()
                            val statsMap = stats.associate { it.category to it.totalBytes }

                            val totalDevice = FastStorageScanner.getTotalStorageBytes(rootDirectory)
                            val freeDevice = FastStorageScanner.getFreeStorageBytes(rootDirectory)
                            val usedDevice = totalDevice - freeDevice

                            val snapshot = ScanSnapshotEntity(
                                volumePath = rootDirectory.absolutePath,
                                volumeName = "Internal Storage",
                                totalDeviceBytes = totalDevice,
                                usedBytes = usedDevice,
                                freeBytes = freeDevice,
                                totalFiles = update.result.totalFiles,
                                videosBytes = statsMap[StorageCategory.VIDEOS.name] ?: 0L,
                                imagesBytes = statsMap[StorageCategory.IMAGES.name] ?: 0L,
                                appsBytes = 0L,
                                documentsBytes = statsMap[StorageCategory.DOCUMENTS.name] ?: 0L,
                                audioBytes = statsMap[StorageCategory.AUDIO.name] ?: 0L,
                                archivesBytes = statsMap[StorageCategory.ARCHIVES.name] ?: 0L,
                                apksBytes = statsMap[StorageCategory.APKS.name] ?: 0L,
                                otherBytes = statsMap[StorageCategory.OTHER.name] ?: 0L,
                                scanDurationMs = update.result.durationMs
                            )
                            database.scanSnapshotDao().insertSnapshot(snapshot)

                            _scanState.value = ScanState.Completed(
                                totalFiles = update.result.totalFiles,
                                totalBytes = update.result.totalBytes,
                                durationMs = update.result.durationMs
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                _scanState.value = ScanState.Cancelled
            } catch (e: Exception) {
                _scanState.value = ScanState.Error(e.localizedMessage ?: "Scan failed unexpectedly")
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        _scanState.value = ScanState.Cancelled
    }

    suspend fun deleteFileRecord(path: String) = withContext(Dispatchers.IO) {
        database.fileDao().deleteByPath(path)
    }

    suspend fun deleteFileRecords(paths: List<String>) = withContext(Dispatchers.IO) {
        if (paths.isEmpty()) return@withContext
        paths.chunked(500).forEach { chunk ->
            database.fileDao().deleteByPaths(chunk)
        }
    }

    suspend fun insertFile(file: FileEntity) = withContext(Dispatchers.IO) {
        database.fileDao().insertBatch(listOf(file))
    }

    suspend fun insertFiles(files: List<FileEntity>) = withContext(Dispatchers.IO) {
        database.fileDao().insertBatch(files)
    }

    suspend fun getFileByPath(path: String): FileEntity? = withContext(Dispatchers.IO) {
        database.fileDao().getFileByPath(path)
    }

    suspend fun purgeExcludedFiles(excludedPaths: Set<String>) = withContext(Dispatchers.IO) {
        if (excludedPaths.isEmpty()) return@withContext
        database.fileDao().purgeExcludedFiles { path ->
            ExclusionMatcher.isExcluded(path, excludedPaths)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: ScanRepository? = null

        fun getInstance(context: Context): ScanRepository {
            return INSTANCE ?: synchronized(this) {
                val db = QuarryDatabase.getInstance(context)
                val prefs = UserPreferencesRepository.getInstance(context)
                val instance = ScanRepository(db, userPreferences = prefs)
                INSTANCE = instance
                instance
            }
        }
    }
}
