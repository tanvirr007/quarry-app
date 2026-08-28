package app.quarry.tanvir.info.data.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "scan_snapshots")
data class ScanSnapshotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val volumePath: String,
    val volumeName: String,
    val totalDeviceBytes: Long,
    val usedBytes: Long,
    val freeBytes: Long,
    val totalFiles: Long,
    val videosBytes: Long = 0,
    val imagesBytes: Long = 0,
    val appsBytes: Long = 0,
    val documentsBytes: Long = 0,
    val audioBytes: Long = 0,
    val archivesBytes: Long = 0,
    val apksBytes: Long = 0,
    val otherBytes: Long = 0,
    val scanDurationMs: Long = 0
)

@Dao
interface ScanSnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: ScanSnapshotEntity): Long

    @Query("SELECT * FROM scan_snapshots ORDER BY timestamp DESC")
    fun getAllSnapshots(): Flow<List<ScanSnapshotEntity>>

    @Query("SELECT * FROM scan_snapshots ORDER BY timestamp DESC")
    suspend fun getAllSnapshotsSync(): List<ScanSnapshotEntity>

    @Query("SELECT * FROM scan_snapshots ORDER BY timestamp DESC LIMIT 1")
    fun getLatestSnapshot(): Flow<ScanSnapshotEntity?>

    @Query("SELECT * FROM scan_snapshots ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestSnapshotSync(): ScanSnapshotEntity?

    @Query("SELECT * FROM scan_snapshots WHERE id = :id")
    suspend fun getSnapshotById(id: Long): ScanSnapshotEntity?

    @Query("DELETE FROM scan_snapshots WHERE id = :id")
    suspend fun deleteSnapshot(id: Long)

    @Query("DELETE FROM scan_snapshots")
    suspend fun clearSnapshots()
}
