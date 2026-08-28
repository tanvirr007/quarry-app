package app.quarry.tanvir.info.domain.volume

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import app.quarry.tanvir.info.data.filesystem.FastStorageScanner
import java.io.File

enum class VolumeAccessMode(val title: String) {
    DIRECT_FILESYSTEM("Direct Filesystem Access"),
    SAF_ONLY("Storage Access Framework (SAF) Only"),
    NOT_CONNECTED("Not Connected")
}

data class StorageVolumeInfo(
    val id: String,
    val name: String,
    val path: String,
    val totalBytes: Long,
    val usedBytes: Long,
    val freeBytes: Long,
    val isPrimary: Boolean,
    val isRemovable: Boolean,
    val accessMode: VolumeAccessMode,
    val supportsTreemap: Boolean,
    val supportsDuplicateScan: Boolean,
    val supportsTrash: Boolean,
    val statusDescription: String
)

class StorageVolumeManager(private val context: Context) {

    fun getDetectedVolumes(): List<StorageVolumeInfo> {
        val volumeList = mutableListOf<StorageVolumeInfo>()
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager

        if (storageManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val volumes = storageManager.storageVolumes
            for (vol in volumes) {
                // Only consider mounted volumes
                val state = vol.state
                if (state != Environment.MEDIA_MOUNTED && state != Environment.MEDIA_MOUNTED_READ_ONLY) {
                    continue
                }

                val isPrimary = vol.isPrimary
                val isRemovable = vol.isRemovable
                val name = vol.getDescription(context) ?: if (isPrimary) "Internal Storage" else "External Storage"
                val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    vol.directory
                } else {
                    if (isPrimary) Environment.getExternalStorageDirectory() else null
                } ?: if (isPrimary) Environment.getExternalStorageDirectory() else null

                val total: Long
                val free: Long
                val used: Long
                val isDirectAccess: Boolean

                if (dir != null && dir.exists()) {
                    total = FastStorageScanner.getTotalStorageBytes(dir)
                    free = FastStorageScanner.getFreeStorageBytes(dir)
                    used = (total - free).coerceAtLeast(0L)
                    isDirectAccess = dir.canRead()
                } else {
                    total = 0L
                    free = 0L
                    used = 0L
                    isDirectAccess = false
                }

                val accessMode = if (isDirectAccess) {
                    VolumeAccessMode.DIRECT_FILESYSTEM
                } else {
                    VolumeAccessMode.SAF_ONLY
                }

                val id = if (isPrimary) "internal_storage" else (vol.uuid ?: "external_${vol.hashCode()}")

                volumeList.add(
                    StorageVolumeInfo(
                        id = id,
                        name = name,
                        path = dir?.absolutePath ?: (if (isPrimary) Environment.getExternalStorageDirectory().absolutePath else "/storage/$id"),
                        totalBytes = total,
                        usedBytes = used,
                        freeBytes = free,
                        isPrimary = isPrimary,
                        isRemovable = isRemovable,
                        accessMode = accessMode,
                        supportsTreemap = isDirectAccess,
                        supportsDuplicateScan = isDirectAccess,
                        supportsTrash = isDirectAccess,
                        statusDescription = if (isPrimary) {
                            "Full feature support: Interactive Treemap, duplicate detection, cleanup, and trash restoration."
                        } else if (isDirectAccess) {
                            "Direct filesystem access available. Full Treemap and duplicate scanning enabled."
                        } else {
                            "Storage Access Framework (SAF) mode. Browse, search, and delete supported."
                        }
                    )
                )
            }
        }

        // Fallback if system storageManager returned empty list
        if (volumeList.isEmpty()) {
            val internalDir = Environment.getExternalStorageDirectory()
            val totalInternal = FastStorageScanner.getTotalStorageBytes(internalDir)
            val freeInternal = FastStorageScanner.getFreeStorageBytes(internalDir)
            val usedInternal = (totalInternal - freeInternal).coerceAtLeast(0L)

            volumeList.add(
                StorageVolumeInfo(
                    id = "internal_storage",
                    name = "Internal Storage",
                    path = internalDir.absolutePath,
                    totalBytes = totalInternal,
                    usedBytes = usedInternal,
                    freeBytes = freeInternal,
                    isPrimary = true,
                    isRemovable = false,
                    accessMode = VolumeAccessMode.DIRECT_FILESYSTEM,
                    supportsTreemap = true,
                    supportsDuplicateScan = true,
                    supportsTrash = true,
                    statusDescription = "Full feature support: Interactive Treemap, duplicate detection, cleanup, and trash restoration."
                )
            )
        }

        return volumeList
    }
}
