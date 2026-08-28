package app.quarry.tanvir.info

import app.quarry.tanvir.info.data.database.ScanSnapshotEntity
import app.quarry.tanvir.info.domain.analyzer.ScanComparisonEngine
import app.quarry.tanvir.info.domain.model.StorageCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanComparisonEngineTest {

    @Test
    fun testSnapshotComparison() {
        val totalBytes = 128L * 1024 * 1024 * 1024

        val previous = ScanSnapshotEntity(
            id = 1,
            timestamp = 1700000000000L,
            volumePath = "/storage/emulated/0",
            volumeName = "Internal Storage",
            totalDeviceBytes = totalBytes,
            usedBytes = 60L * 1024 * 1024 * 1024,
            freeBytes = 68L * 1024 * 1024 * 1024,
            totalFiles = 10000,
            videosBytes = 20L * 1024 * 1024 * 1024,
            imagesBytes = 15L * 1024 * 1024 * 1024
        )

        val latest = ScanSnapshotEntity(
            id = 2,
            timestamp = 1705000000000L,
            volumePath = "/storage/emulated/0",
            volumeName = "Internal Storage",
            totalDeviceBytes = totalBytes,
            usedBytes = 65L * 1024 * 1024 * 1024, // +5 GB
            freeBytes = 63L * 1024 * 1024 * 1024,
            totalFiles = 10500,
            videosBytes = 23L * 1024 * 1024 * 1024, // +3 GB
            imagesBytes = 17L * 1024 * 1024 * 1024  // +2 GB
        )

        val result = ScanComparisonEngine.compareSnapshots(latest, previous)

        assertEquals(5L * 1024 * 1024 * 1024, result.totalDeltaBytes)
        assertEquals("+5.0 GB", result.formattedTotalDelta)
        assertTrue(result.isIncrease)

        val videoDelta = result.categoryDeltas.first { it.category == StorageCategory.VIDEOS }
        assertEquals(3L * 1024 * 1024 * 1024, videoDelta.deltaBytes)
        assertEquals("+3.0 GB", videoDelta.formattedDelta)

        val imageDelta = result.categoryDeltas.first { it.category == StorageCategory.IMAGES }
        assertEquals(2L * 1024 * 1024 * 1024, imageDelta.deltaBytes)
        assertEquals("+2.0 GB", imageDelta.formattedDelta)
    }
}
