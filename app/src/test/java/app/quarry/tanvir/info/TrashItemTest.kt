package app.quarry.tanvir.info

import app.quarry.tanvir.info.domain.file.TrashItem
import app.quarry.tanvir.info.domain.model.StorageCategory
import app.quarry.tanvir.info.domain.model.StorageFormatter
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashItemTest {

    @Test
    fun testTrashItemSerializationAndParsing() {
        val item = TrashItem(
            id = "1724889600000_abc123_video.mp4",
            originalPath = "/storage/emulated/0/Movies/video.mp4",
            trashPath = "/storage/emulated/0/Android/data/app.quarry.tanvir.info/files/.quarry_trash/1724889600000_abc123_video.mp4",
            name = "video.mp4",
            size = 104857600L,
            isDirectory = false,
            deletedTimestamp = 1724889600000L
        )

        val json = JSONObject().apply {
            put("id", item.id)
            put("originalPath", item.originalPath)
            put("trashPath", item.trashPath)
            put("name", item.name)
            put("size", item.size)
            put("isDirectory", item.isDirectory)
            put("deletedTimestamp", item.deletedTimestamp)
        }

        val array = JSONArray().apply { put(json) }
        val arrayString = array.toString()

        val parsedArray = JSONArray(arrayString)
        assertEquals(1, parsedArray.length())
        val parsedObj = parsedArray.getJSONObject(0)

        assertEquals(item.id, parsedObj.getString("id"))
        assertEquals(item.originalPath, parsedObj.getString("originalPath"))
        assertEquals(item.trashPath, parsedObj.getString("trashPath"))
        assertEquals(item.name, parsedObj.getString("name"))
        assertEquals(item.size, parsedObj.getLong("size"))
        assertFalse(parsedObj.getBoolean("isDirectory"))
        assertEquals(item.deletedTimestamp, parsedObj.getLong("deletedTimestamp"))
    }

    @Test
    fun testTrashItemCategoryAndFormatting() {
        val ext = "mp4"
        val category = StorageCategory.fromExtension(ext)
        assertEquals(StorageCategory.VIDEOS, category)

        val formattedSize = StorageFormatter.formatBytes(104857600L)
        assertEquals("100.0 MB", formattedSize)
    }
}
