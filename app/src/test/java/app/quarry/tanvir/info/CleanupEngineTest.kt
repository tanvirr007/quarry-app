package app.quarry.tanvir.info

import app.quarry.tanvir.info.domain.cleanup.DefaultCleanupEngine
import app.quarry.tanvir.info.domain.model.StorageCategory
import app.quarry.tanvir.info.domain.model.StorageItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CleanupEngineTest {

    private val cleanupEngine = DefaultCleanupEngine()

    @Test
    fun testCleanupCandidateCategorization() = runBlocking {
        val now = System.currentTimeMillis()
        val sevenMonthsAgo = now - (210L * 24 * 60 * 60 * 1000)

        val items = listOf(
            // Large file (> 50 MB)
            StorageItem(
                id = 1,
                path = "/storage/emulated/0/Movies/large_movie.mkv",
                name = "large_movie.mkv",
                size = 120L * 1024 * 1024,
                isDirectory = false,
                category = StorageCategory.VIDEOS,
                lastModified = now
            ),
            // APK file
            StorageItem(
                id = 2,
                path = "/storage/emulated/0/Download/installer.apk",
                name = "installer.apk",
                size = 25L * 1024 * 1024,
                isDirectory = false,
                category = StorageCategory.APKS,
                lastModified = now
            ),
            // Screenshot
            StorageItem(
                id = 3,
                path = "/storage/emulated/0/DCIM/Screenshots/Screenshot_2026.png",
                name = "Screenshot_2026.png",
                size = 2L * 1024 * 1024,
                isDirectory = false,
                category = StorageCategory.IMAGES,
                lastModified = now,
                isScreenshot = true
            ),
            // Old untouched file (> 6 months)
            StorageItem(
                id = 4,
                path = "/storage/emulated/0/Documents/old_notes.pdf",
                name = "old_notes.pdf",
                size = 5L * 1024 * 1024,
                isDirectory = false,
                category = StorageCategory.DOCUMENTS,
                lastModified = sevenMonthsAgo
            ),
            // Temporary junk file
            StorageItem(
                id = 5,
                path = "/storage/emulated/0/cache/temp_dump.tmp",
                name = "temp_dump.tmp",
                size = 10L * 1024 * 1024,
                isDirectory = false,
                category = StorageCategory.OTHER,
                extension = "tmp",
                lastModified = now
            )
        )

        val candidates = cleanupEngine.getCandidates(items)

        // Verify categorized groups are created
        val titles = candidates.map { it.title }
        assertTrue(titles.contains("Large Files"))
        assertTrue(titles.contains("Old APK Files"))
        assertTrue(titles.contains("Screenshots"))
        assertTrue(titles.contains("Old & Untouched Files"))
        assertTrue(titles.contains("Temporary & Log Files"))

        val largeFilesGroup = candidates.first { it.title == "Large Files" }
        assertEquals(1, largeFilesGroup.items.size)
        assertEquals(120L * 1024 * 1024, largeFilesGroup.totalBytes)
    }
}
