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

        // 1. Primary Internal Storage
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

        // 2. Removable SD Cards and External Volumes
        val externalDirs = context.getExternalFilesDirs(null)
        for (file in externalDirs) {
            if (file != null && !file.absolutePath.startsWith(internalDir.absolutePath)) {
                val rootPath = file.absolutePath.substringBefore("/Android")
                val rootFile = File(rootPath)
                val total = FastStorageScanner.getTotalStorageBytes(rootFile)
                val free = FastStorageScanner.getFreeStorageBytes(rootFile)
                val used = (total - free).coerceAtLeast(0L)

                val isDirectAccess = rootFile.canRead()

                volumeList.add(
                    StorageVolumeInfo(
                        id = "sd_card_${rootFile.name}",
                        name = "SD Card (${rootFile.name})",
                        path = rootPath,
                        totalBytes = total,
                        usedBytes = used,
                        freeBytes = free,
                        isPrimary = false,
                        isRemovable = true,
                        accessMode = if (isDirectAccess) VolumeAccessMode.DIRECT_FILESYSTEM else VolumeAccessMode.SAF_ONLY,
                        supportsTreemap = isDirectAccess,
                        supportsDuplicateScan = isDirectAccess,
                        supportsTrash = isDirectAccess,
                        statusDescription = if (isDirectAccess) {
                            "Direct filesystem access available. Full Treemap and duplicate scanning enabled."
                        } else {
                            "SAF fallback mode. Browse, search, and delete supported. Real-time treemap disabled."
                        }
                    )
                )
            }
        }

        // 3. USB OTG Entry (Status tracking)
        val hasUsbOtg = volumeList.any { it.name.contains("USB", ignoreCase = true) }
        if (!hasUsbOtg) {
            volumeList.add(
                StorageVolumeInfo(
                    id = "usb_otg",
                    name = "USB OTG Storage",
                    path = "/storage/usb",
                    totalBytes = 0L,
                    usedBytes = 0L,
                    freeBytes = 0L,
                    isPrimary = false,
                    isRemovable = true,
                    accessMode = VolumeAccessMode.NOT_CONNECTED,
                    supportsTreemap = false,
                    supportsDuplicateScan = false,
                    supportsTrash = false,
                    statusDescription = "Not connected. When connected, SAF access enables file browsing, search, and delete."
                )
            )
        }

        return volumeList
    }
}
