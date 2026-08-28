package app.quarry.tanvir.info

import app.quarry.tanvir.info.data.database.CategoryStat
import app.quarry.tanvir.info.data.database.ScanSnapshotEntity
import app.quarry.tanvir.info.domain.analyzer.StorageAnalyzer
import app.quarry.tanvir.info.domain.model.StorageCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageAnalyzerTest {

    @Test
    fun testStorageOverviewCalculations() {
        val totalBytes = 128L * 1024 * 1024 * 1024 // 128 GB
        val freeBytes = 40L * 1024 * 1024 * 1024   // 40 GB
        val usedBytes = totalBytes - freeBytes      // 88 GB

        val categoryStats = listOf(
            CategoryStat(StorageCategory.VIDEOS.name, 25L * 1024 * 1024 * 1024, 150),
            CategoryStat(StorageCategory.IMAGES.name, 15L * 1024 * 1024 * 1024, 3200),
            CategoryStat(StorageCategory.AUDIO.name, 8L * 1024 * 1024 * 1024, 450)
        )

        val snapshots = listOf(
            ScanSnapshotEntity(
                id = 2,
                volumePath = "/storage/emulated/0",
                volumeName = "Internal Storage",
                totalDeviceBytes = totalBytes,
                usedBytes = usedBytes,
                freeBytes = freeBytes,
                totalFiles = 3800
            ),
            ScanSnapshotEntity(
                id = 1,
                volumePath = "/storage/emulated/0",
                volumeName = "Internal Storage",
                totalDeviceBytes = totalBytes,
                usedBytes = usedBytes - (2L * 1024 * 1024 * 1024), // 2 GB less previously
                freeBytes = freeBytes + (2L * 1024 * 1024 * 1024),
                totalFiles = 3500
            )
        )

        val overview = StorageAnalyzer.calculateOverview(
            volumeName = "Internal Storage",
            volumePath = "/storage/emulated/0",
            totalBytes = totalBytes,
            freeBytes = freeBytes,
            categoryStats = categoryStats,
            snapshots = snapshots,
            largeFilesSize = 5L * 1024 * 1024 * 1024,
            largeFilesCount = 12,
            apksSize = 1L * 1024 * 1024 * 1024,
            apksCount = 4
        )

        assertEquals("Internal Storage", overview.volumeName)
        assertEquals(totalBytes, overview.totalBytes)
        assertEquals(usedBytes, overview.usedBytes)
        assertEquals(freeBytes, overview.freeBytes)
        assertEquals(3800L, overview.totalFiles)
        assertEquals(8, overview.categoryBreakdown.size)
        assertTrue(overview.storageGrowthText?.contains("+2.0 GB") == true)
        assertEquals(2, overview.quickInsights.size)
    }
}
