package app.quarry.tanvir.info.domain.analyzer

import app.quarry.tanvir.info.data.database.ScanSnapshotEntity
import app.quarry.tanvir.info.domain.model.StorageCategory
import app.quarry.tanvir.info.domain.model.StorageFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class CategoryDelta(
    val category: StorageCategory,
    val deltaBytes: Long,
    val formattedDelta: String
)

data class ScanComparisonResult(
    val latestSnapshot: ScanSnapshotEntity,
    val previousSnapshot: ScanSnapshotEntity,
    val totalDeltaBytes: Long,
    val formattedTotalDelta: String,
    val isIncrease: Boolean,
    val summaryText: String,
    val categoryDeltas: List<CategoryDelta>
)

object ScanComparisonEngine {

    fun compareSnapshots(
        latest: ScanSnapshotEntity,
        previous: ScanSnapshotEntity
    ): ScanComparisonResult {
        val totalDelta = latest.usedBytes - previous.usedBytes
        val isIncrease = totalDelta >= 0
        val formattedTotal = StorageFormatter.formatBytes(abs(totalDelta))

        val dateFormat = SimpleDateFormat("MMM dd", Locale.US)
        val prevDate = dateFormat.format(Date(previous.timestamp))
        val latestDate = dateFormat.format(Date(latest.timestamp))

        val summary = if (totalDelta > 0) {
            "Storage increased by $formattedTotal between $prevDate and $latestDate"
        } else if (totalDelta < 0) {
            "Storage decreased by $formattedTotal between $prevDate and $latestDate"
        } else {
            "No change in storage usage between $prevDate and $latestDate"
        }

        val deltas = listOf(
            CategoryDelta(
                StorageCategory.VIDEOS,
                latest.videosBytes - previous.videosBytes,
                formatDelta(latest.videosBytes - previous.videosBytes)
            ),
            CategoryDelta(
                StorageCategory.IMAGES,
                latest.imagesBytes - previous.imagesBytes,
                formatDelta(latest.imagesBytes - previous.imagesBytes)
            ),
            CategoryDelta(
                StorageCategory.DOCUMENTS,
                latest.documentsBytes - previous.documentsBytes,
                formatDelta(latest.documentsBytes - previous.documentsBytes)
            ),
            CategoryDelta(
                StorageCategory.AUDIO,
                latest.audioBytes - previous.audioBytes,
                formatDelta(latest.audioBytes - previous.audioBytes)
            ),
            CategoryDelta(
                StorageCategory.ARCHIVES,
                latest.archivesBytes - previous.archivesBytes,
                formatDelta(latest.archivesBytes - previous.archivesBytes)
            ),
            CategoryDelta(
                StorageCategory.APKS,
                latest.apksBytes - previous.apksBytes,
                formatDelta(latest.apksBytes - previous.apksBytes)
            ),
            CategoryDelta(
                StorageCategory.OTHER,
                latest.otherBytes - previous.otherBytes,
                formatDelta(latest.otherBytes - previous.otherBytes)
            )
        )

        return ScanComparisonResult(
            latestSnapshot = latest,
            previousSnapshot = previous,
            totalDeltaBytes = totalDelta,
            formattedTotalDelta = (if (isIncrease) "+" else "-") + formattedTotal,
            isIncrease = isIncrease,
            summaryText = summary,
            categoryDeltas = deltas
        )
    }

    private fun formatDelta(bytes: Long): String {
        val formatted = StorageFormatter.formatBytes(abs(bytes))
        return when {
            bytes > 0 -> "+$formatted"
            bytes < 0 -> "-$formatted"
            else -> "0 B"
        }
    }
}
