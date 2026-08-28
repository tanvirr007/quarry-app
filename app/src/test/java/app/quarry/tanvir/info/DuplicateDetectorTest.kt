package app.quarry.tanvir.info

import app.quarry.tanvir.info.domain.duplicates.DuplicateGroup
import app.quarry.tanvir.info.domain.duplicates.FastDuplicateDetector
import app.quarry.tanvir.info.domain.model.StorageCategory
import app.quarry.tanvir.info.domain.model.StorageItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class DuplicateDetectorTest {

    @Test
    fun testDuplicateGroupRecoverableCalculation() {
        val item1 = StorageItem(
            id = 1,
            path = "/storage/emulated/0/Download/video.mp4",
            name = "video.mp4",
            size = 1000L,
            isDirectory = false,
            category = StorageCategory.VIDEOS
        )
        val item2 = StorageItem(
            id = 2,
            path = "/storage/emulated/0/Movies/video_copy.mp4",
            name = "video_copy.mp4",
            size = 1000L,
            isDirectory = false,
            category = StorageCategory.VIDEOS
        )
        val item3 = StorageItem(
            id = 3,
            path = "/storage/emulated/0/DCIM/video_bak.mp4",
            name = "video_bak.mp4",
            size = 1000L,
            isDirectory = false,
            category = StorageCategory.VIDEOS
        )

        val group = DuplicateGroup(
            size = 1000L,
            items = listOf(item1, item2, item3),
            hash = "sha256dummy"
        )

        // 3 items of 1000 bytes each -> keeping 1 means recovering 2 * 1000 = 2000 bytes
        assertEquals(2000L, group.recoverableBytes)
    }

    @Test
    fun testSingleItemRecoverableIsZero() {
        val item1 = StorageItem(
            id = 1,
            path = "/storage/emulated/0/Download/single.mp4",
            name = "single.mp4",
            size = 500L,
            isDirectory = false,
            category = StorageCategory.VIDEOS
        )
        val group = DuplicateGroup(
            size = 500L,
            items = listOf(item1)
        )
        assertEquals(0L, group.recoverableBytes)
    }
}
