package app.quarry.tanvir.info.domain.analyzer

import app.quarry.tanvir.info.data.database.CategoryStat
import app.quarry.tanvir.info.data.database.ScanSnapshotEntity
import app.quarry.tanvir.info.domain.model.StorageCategory

data class StorageCategoryData(
    val category: StorageCategory,
    val totalBytes: Long,
    val fileCount: Long,
    val percentageOfUsed: Float = 0f
)

data class QuickInsight(
    val id: String,
    val title: String,
    val formattedSize: String,
    val description: String,
    val category: StorageCategory,
    val itemCount: Long = 0
)

data class StorageOverviewData(
    val volumeName: String = "Internal Storage",
    val volumePath: String = "",
    val totalBytes: Long = 0,
    val usedBytes: Long = 0,
    val freeBytes: Long = 0,
    val usedPercentage: Float = 0f,
    val categoryBreakdown: List<StorageCategoryData> = emptyList(),
    val quickInsights: List<QuickInsight> = emptyList(),
    val storageGrowthText: String? = null,
    val totalFiles: Long = 0
)

object StorageAnalyzer {

    fun calculateOverview(
        volumeName: String,
        volumePath: String,
        totalBytes: Long,
        freeBytes: Long,
        categoryStats: List<CategoryStat>,
        snapshots: List<ScanSnapshotEntity>,
        largeFilesSize: Long = 0L,
        largeFilesCount: Long = 0L,
        apksSize: Long = 0L,
        apksCount: Long = 0L,
        screenshotsSize: Long = 0L,
        screenshotsCount: Long = 0L,
        duplicatesSize: Long = 0L,
        duplicatesCount: Long = 0L
    ): StorageOverviewData {
        val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)
        val usedPercentage = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

        val statsMap = categoryStats.associate { it.category to it }

        var totalFilesCount = 0L
        val categoryDataList = StorageCategory.entries.map { category ->
            val stat = statsMap[category.name]
            val catBytes = stat?.totalBytes ?: 0L
            val catCount = stat?.fileCount ?: 0L
            totalFilesCount += catCount
            val pct = if (usedBytes > 0) (catBytes.toFloat() / usedBytes.toFloat()).coerceIn(0f, 1f) else 0f

            StorageCategoryData(
                category = category,
                totalBytes = catBytes,
                fileCount = catCount,
                percentageOfUsed = pct
            )
        }

        val insights = mutableListOf<QuickInsight>()
        if (largeFilesSize > 0) {
            insights.add(
                QuickInsight(
                    id = "large_files",
                    title = "Large Files",
                    formattedSize = app.quarry.tanvir.info.domain.model.StorageFormatter.formatBytes(largeFilesSize),
                    description = "$largeFilesCount files over 50 MB",
                    category = StorageCategory.OTHER,
                    itemCount = largeFilesCount
                )
            )
        }
        if (duplicatesSize > 0) {
            insights.add(
                QuickInsight(
                    id = "duplicates",
                    title = "Potential Duplicates",
                    formattedSize = app.quarry.tanvir.info.domain.model.StorageFormatter.formatBytes(duplicatesSize),
                    description = "$duplicatesCount duplicate candidates",
                    category = StorageCategory.DOCUMENTS,
                    itemCount = duplicatesCount
                )
            )
        }
        if (apksSize > 0) {
            insights.add(
                QuickInsight(
                    id = "apks",
                    title = "APK Files",
                    formattedSize = app.quarry.tanvir.info.domain.model.StorageFormatter.formatBytes(apksSize),
                    description = "$apksCount APK install packages",
                    category = StorageCategory.APKS,
                    itemCount = apksCount
                )
            )
        }
        if (screenshotsSize > 0) {
            insights.add(
                QuickInsight(
                    id = "screenshots",
                    title = "Screenshots",
                    formattedSize = app.quarry.tanvir.info.domain.model.StorageFormatter.formatBytes(screenshotsSize),
                    description = "$screenshotsCount captured screenshots",
                    category = StorageCategory.IMAGES,
                    itemCount = screenshotsCount
                )
            )
        }

        // Calculate growth comparison if snapshots are available
        var growthText: String? = null
        if (snapshots.size >= 2) {
            val latest = snapshots[0]
            val previous = snapshots[1]
            val diff = latest.usedBytes - previous.usedBytes
            val formattedDiff = app.quarry.tanvir.info.domain.model.StorageFormatter.formatBytes(Math.abs(diff))
            growthText = if (diff > 0) {
                "+$formattedDiff since last scan"
            } else if (diff < 0) {
                "-$formattedDiff since last scan"
            } else {
                "No change since last scan"
            }
        }

        return StorageOverviewData(
            volumeName = volumeName,
            volumePath = volumePath,
            totalBytes = totalBytes,
            usedBytes = usedBytes,
            freeBytes = freeBytes,
            usedPercentage = usedPercentage,
            categoryBreakdown = categoryDataList,
            quickInsights = insights,
            storageGrowthText = growthText,
            totalFiles = totalFilesCount
        )
    }
}
