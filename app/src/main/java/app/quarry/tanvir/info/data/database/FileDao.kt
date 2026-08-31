package app.quarry.tanvir.info.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface FileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(files: List<FileEntity>)

    @Query("DELETE FROM files")
    suspend fun clearAll()

    @Query("DELETE FROM files WHERE path = :path OR path LIKE :path || '/%'")
    suspend fun deleteByPath(path: String)

    @Query("DELETE FROM files WHERE path IN (:paths)")
    suspend fun deleteByPaths(paths: List<String>)

    @Query("SELECT * FROM files")
    fun getAllFiles(): Flow<List<FileEntity>>

    @Query("SELECT * FROM files")
    suspend fun getAllFilesSync(): List<FileEntity>

    @Query("SELECT * FROM files WHERE isDirectory = 0")
    fun getAllNonDirectoryFiles(): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE isDirectory = 0")
    suspend fun getAllNonDirectoryFilesSync(): List<FileEntity>

    @Query("SELECT COUNT(*) FROM files WHERE isDirectory = 0")
    fun getTotalFileCount(): Flow<Long>

    @Query("SELECT SUM(size) FROM files WHERE isDirectory = 0")
    fun getTotalScannedBytes(): Flow<Long?>

    @Query("SELECT category, SUM(size) AS totalBytes, COUNT(*) AS fileCount FROM files WHERE isDirectory = 0 GROUP BY category")
    fun getCategoryStats(): Flow<List<CategoryStat>>

    @Query("SELECT category, SUM(size) AS totalBytes, COUNT(*) AS fileCount FROM files WHERE isDirectory = 0 GROUP BY category")
    suspend fun getCategoryStatsSync(): List<CategoryStat>

    @Query("SELECT * FROM files WHERE parentPath = :parentPath ORDER BY isDirectory DESC, size DESC")
    fun getChildren(parentPath: String): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE parentPath = :parentPath ORDER BY isDirectory DESC, size DESC")
    suspend fun getChildrenSync(parentPath: String): List<FileEntity>

    @Query("SELECT * FROM files WHERE path = :path LIMIT 1")
    suspend fun getFileByPath(path: String): FileEntity?

    @Query("SELECT * FROM files WHERE isDirectory = 0 ORDER BY size DESC LIMIT :limit")
    fun getLargestFiles(limit: Int): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE category = :category AND isDirectory = 0 ORDER BY size DESC")
    fun getFilesByCategory(category: String): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE name LIKE '%' || :query || '%' OR path LIKE '%' || :query || '%' ORDER BY isDirectory DESC, size DESC LIMIT 200")
    fun searchFiles(query: String): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE isScreenshot = 1 AND isDirectory = 0 ORDER BY lastModified DESC")
    fun getScreenshots(): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE isDownload = 1 AND isDirectory = 0 ORDER BY lastModified DESC")
    fun getDownloads(): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE category = 'APKS' AND isDirectory = 0 ORDER BY size DESC")
    fun getApkFiles(): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE size >= :minSizeBytes AND isDirectory = 0 ORDER BY size DESC")
    fun getLargeFiles(minSizeBytes: Long): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE lastModified <= :beforeTimestamp AND isDirectory = 0 ORDER BY lastModified ASC")
    fun getOldFiles(beforeTimestamp: Long): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE isDirectory = 1 AND directChildrenCount = 0")
    fun getEmptyFolders(): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE size IN (SELECT size FROM files WHERE isDirectory = 0 GROUP BY size HAVING COUNT(*) > 1) AND isDirectory = 0 ORDER BY size DESC")
    suspend fun getPotentialDuplicateSizeCandidates(): List<FileEntity>

    @Transaction
    suspend fun purgeExcludedFiles(isExcluded: (String) -> Boolean) {
        val allFiles = getAllFilesSync()
        val pathsToDelete = allFiles.filter { isExcluded(it.path) }.map { it.path }
        if (pathsToDelete.isNotEmpty()) {
            pathsToDelete.chunked(500).forEach { chunk ->
                deleteByPaths(chunk)
            }
        }
    }

    @Transaction
    suspend fun renamePathPrefix(oldPath: String, newPath: String, updatedEntity: FileEntity) {
        val allMatching = getAllFilesSync().filter { it.path.startsWith("$oldPath/") }
        val updatedChildren = allMatching.map { entity ->
            val updatedPath = newPath + entity.path.removePrefix(oldPath)
            val updatedParent = entity.parentPath?.let { parent ->
                if (parent == oldPath) newPath
                else if (parent.startsWith("$oldPath/")) newPath + parent.removePrefix(oldPath)
                else parent
            }
            entity.copy(
                path = updatedPath,
                parentPath = updatedParent
            )
        }
        deleteByPath(oldPath)
        val allToInsert = listOf(updatedEntity) + updatedChildren
        allToInsert.chunked(500).forEach { chunk ->
            insertBatch(chunk)
        }
    }

    @Transaction
    suspend fun replaceAllFiles(files: List<FileEntity>) {
        clearAll()
        // Insert in chunks of 500 to keep memory footprint flat
        files.chunked(500).forEach { chunk ->
            insertBatch(chunk)
        }
    }
}
