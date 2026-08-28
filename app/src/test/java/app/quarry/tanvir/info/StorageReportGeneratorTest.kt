package app.quarry.tanvir.info

import app.quarry.tanvir.info.data.database.CategoryStat
import app.quarry.tanvir.info.data.database.FileEntity
import app.quarry.tanvir.info.domain.model.StorageCategory
import app.quarry.tanvir.info.domain.report.StorageReportGenerator
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageReportGeneratorTest {

    @Test
    fun testGenerateReportText() {
        val totalBytes = 128L * 1024 * 1024 * 1024
        val usedBytes = 64L * 1024 * 1024 * 1024
        val freeBytes = 64L * 1024 * 1024 * 1024

        val categoryStats = listOf(
            CategoryStat(StorageCategory.VIDEOS.name, 30L * 1024 * 1024 * 1024, 40),
            CategoryStat(StorageCategory.IMAGES.name, 20L * 1024 * 1024 * 1024, 1500)
        )

        val largestFiles = listOf(
            FileEntity(
                path = "/storage/emulated/0/Download/movie.mp4",
                name = "movie.mp4",
                size = 4L * 1024 * 1024 * 1024,
                isDirectory = false,
                category = StorageCategory.VIDEOS.name
            )
        )

        val report = StorageReportGenerator.generateReportText(
            volumeName = "Internal Storage",
            totalBytes = totalBytes,
            usedBytes = usedBytes,
            freeBytes = freeBytes,
            categoryStats = categoryStats,
            largestFiles = largestFiles
        )

        assertTrue(report.contains("QUARRY STORAGE REPORT"))
        assertTrue(report.contains("Total Storage: 128.0 GB"))
        assertTrue(report.contains("Used Storage:  64.0 GB (50.0%)"))
        assertTrue(report.contains("Videos"))
        assertTrue(report.contains("Images"))
        assertTrue(report.contains("movie.mp4"))
        assertTrue(report.contains("100% Offline"))
    }
}
