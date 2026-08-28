package app.quarry.tanvir.info.domain.scanner

import app.quarry.tanvir.info.domain.model.StorageCategory

data class ScanProgress(
    val currentPath: String = "",
    val currentFolderName: String = "",
    val filesScanned: Long = 0,
    val bytesScanned: Long = 0,
    val estimatedTotalBytes: Long = 0,
    val isComplete: Boolean = false,
    val activeFolders: List<FolderScanStatus> = emptyList()
) {
    val progressFraction: Float
        get() = if (estimatedTotalBytes > 0) {
            (bytesScanned.toFloat() / estimatedTotalBytes.toFloat()).coerceIn(0f, 1f)
        } else 0f
}

data class FolderScanStatus(
    val name: String,
    val isCompleted: Boolean = false
)

sealed class ScanState {
    data object Idle : ScanState()
    data class Scanning(val progress: ScanProgress) : ScanState()
    data class Completed(
        val totalFiles: Long,
        val totalBytes: Long,
        val durationMs: Long
    ) : ScanState()
    data class Error(val message: String) : ScanState()
    data object Cancelled : ScanState()
}
