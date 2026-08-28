package app.quarry.tanvir.info

import app.quarry.tanvir.info.domain.model.StorageCategory
import app.quarry.tanvir.info.domain.model.StorageFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

class StorageCategoryTest {

    @Test
    fun testCategoryFromExtension() {
        assertEquals(StorageCategory.VIDEOS, StorageCategory.fromExtension("mp4"))
        assertEquals(StorageCategory.VIDEOS, StorageCategory.fromExtension("MKV"))
        assertEquals(StorageCategory.IMAGES, StorageCategory.fromExtension("jpg"))
        assertEquals(StorageCategory.IMAGES, StorageCategory.fromExtension(".png"))
        assertEquals(StorageCategory.AUDIO, StorageCategory.fromExtension("flac"))
        assertEquals(StorageCategory.DOCUMENTS, StorageCategory.fromExtension("pdf"))
        assertEquals(StorageCategory.DOCUMENTS, StorageCategory.fromExtension("docx"))
        assertEquals(StorageCategory.ARCHIVES, StorageCategory.fromExtension("zip"))
        assertEquals(StorageCategory.ARCHIVES, StorageCategory.fromExtension("tar.gz"))
        assertEquals(StorageCategory.APKS, StorageCategory.fromExtension("apk"))
        assertEquals(StorageCategory.APKS, StorageCategory.fromExtension("xapk"))
        assertEquals(StorageCategory.OTHER, StorageCategory.fromExtension("unknownext123"))
    }

    @Test
    fun testStorageFormatting() {
        assertEquals("0 B", StorageFormatter.formatBytes(0))
        assertEquals("500 B", StorageFormatter.formatBytes(500))
        assertEquals("1.0 KB", StorageFormatter.formatBytes(1024))
        assertEquals("1.5 MB", StorageFormatter.formatBytes((1.5 * 1024 * 1024).toLong()))
        assertEquals("14.8 GB", StorageFormatter.formatBytes((14.8 * 1024 * 1024 * 1024).toLong()))
    }
}
