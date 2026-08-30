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

                val dir: File? = if (isPrimary) {
                    // Primary volume: always use the standard API path
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        vol.directory ?: Environment.getExternalStorageDirectory()
                    } else {
                        Environment.getExternalStorageDirectory()
                    }
                } else {
                    // Non-primary (USB OTG, SD card, etc.): multi-probe resolution
                    resolveAccessibleDirectory(vol.uuid)
                }

                val total: Long
                val free: Long
                val used: Long
                val isDirectAccess: Boolean

                if (dir != null && dir.exists() && dir.canRead()) {
                    total = FastStorageScanner.getTotalStorageBytes(dir)
                    free = FastStorageScanner.getFreeStorageBytes(dir)
                    used = (total - free).coerceAtLeast(0L)
                    isDirectAccess = true
                } else if (dir != null && dir.exists()) {
                    // Path exists but not readable -- try stats anyway (some ROMs allow StatFs but not listing)
                    total = FastStorageScanner.getTotalStorageBytes(dir)
                    free = FastStorageScanner.getFreeStorageBytes(dir)
                    used = (total - free).coerceAtLeast(0L)
                    isDirectAccess = total > 0 && dir.canRead()
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

    /**
     * Resolves the accessible filesystem directory for a non-primary volume.
     *
     * StorageVolume.getDirectory() often returns /mnt/media_rw/<UUID> -- the raw kernel
     * mount point restricted to GID media_rw (1023). Apps cannot read this path.
     * The user-visible FUSE projection is at /storage/<UUID>.
     *
     * This method probes multiple paths in priority order and returns the first one
     * that is both exists() and canRead():
     *
     * 1. /storage/<UUID>  (standard Android FUSE mount)
     * 2. getExternalFilesDirs(null) walk-up  (ROM-agnostic discovery)
     * 3. vol.directory path as-is  (last resort, display-only)
     */
    private fun resolveAccessibleDirectory(uuid: String?): File? {
        if (uuid == null) return null

        // Priority 1: Standard FUSE mount at /storage/<UUID>
        val storagePath = File("/storage/$uuid")
        if (storagePath.exists() && storagePath.canRead()) {
            return storagePath
        }

        // Priority 2: Discover via getExternalFilesDirs (ROM-agnostic).
        // Returns app-scoped dirs on all mounted volumes, e.g.:
        // /storage/EE6E-76E2/Android/data/<pkg>/files
        // Walk up to find the volume root containing this UUID.
        try {
            val externalDirs = context.getExternalFilesDirs(null)
            for (extDir in externalDirs) {
                if (extDir == null) continue
                val extPath = extDir.absolutePath
                if (extPath.contains(uuid, ignoreCase = true)) {
                    // Walk up from the app-scoped dir to the volume root.
                    // The UUID segment marks the volume root: /storage/<UUID>/Android/data/...
                    var candidate = extDir
                    while (candidate.parentFile != null) {
                        if (candidate.name.equals(uuid, ignoreCase = true)) {
                            if (candidate.exists() && candidate.canRead()) {
                                return candidate
                            }
                            break
                        }
                        candidate = candidate.parentFile!!
                    }
                }
            }
        } catch (_: Exception) {
            // getExternalFilesDirs can throw on some devices
        }

        // Priority 3: /mnt/media_rw/<UUID> (raw mount -- usually not readable, but try)
        val rawPath = File("/mnt/media_rw/$uuid")
        if (rawPath.exists() && rawPath.canRead()) {
            return rawPath
        }

        // Return the standard FUSE path for display even if not readable,
        // so the UI shows a meaningful path instead of a generated ID.
        return if (storagePath.exists()) storagePath else rawPath.takeIf { it.exists() }
    }
}
