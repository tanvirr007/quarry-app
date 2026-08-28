package app.quarry.tanvir.info

import app.quarry.tanvir.info.data.database.FileEntity
import app.quarry.tanvir.info.domain.model.StorageCategory
import app.quarry.tanvir.info.domain.treemap.TreemapEngine
import app.quarry.tanvir.info.domain.treemap.TreemapNode
import app.quarry.tanvir.info.domain.treemap.TreemapRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TreemapEngineTest {

    @Test
    fun testSquarifiedTreemapLayout() {
        val nodes = listOf(
            TreemapNode(
                path = "/storage/emulated/0/Download/video.mp4",
                name = "video.mp4",
                size = 600L,
                isDirectory = false,
                category = StorageCategory.VIDEOS
            ),
            TreemapNode(
                path = "/storage/emulated/0/Download/photo.jpg",
                name = "photo.jpg",
                size = 200L,
                isDirectory = false,
                category = StorageCategory.IMAGES
            ),
            TreemapNode(
                path = "/storage/emulated/0/Download/doc.pdf",
                name = "doc.pdf",
                size = 200L,
                isDirectory = false,
                category = StorageCategory.DOCUMENTS
            )
        )

        val bounds = TreemapRect(0f, 0f, 1000f, 1000f)
        val laidOut = TreemapEngine.layoutSquarified(nodes, bounds)

        assertEquals(3, laidOut.size)

        // Verify total area coverage matches bounds
        val totalArea = laidOut.sumOf { (it.rect.width * it.rect.height).toDouble() }
        val targetArea = (bounds.width * bounds.height).toDouble()
        assertEquals(targetArea, totalArea, 10.0)

        // Largest item (60% of total size) should have roughly 60% of total area
        val videoArea = (laidOut[0].rect.width * laidOut[0].rect.height).toDouble()
        assertEquals(targetArea * 0.6, videoArea, targetArea * 0.05)
    }

    @Test
    fun testBuildTreeFromFiles() {
        val files = listOf(
            FileEntity(
                path = "/storage/emulated/0/Download",
                name = "Download",
                size = 800L,
                isDirectory = true,
                category = StorageCategory.OTHER.name,
                parentPath = "/storage/emulated/0",
                directChildrenCount = 2
            ),
            FileEntity(
                path = "/storage/emulated/0/Download/movie.mp4",
                name = "movie.mp4",
                size = 500L,
                isDirectory = false,
                category = StorageCategory.VIDEOS.name,
                parentPath = "/storage/emulated/0/Download",
                extension = "mp4"
            ),
            FileEntity(
                path = "/storage/emulated/0/Download/music.mp3",
                name = "music.mp3",
                size = 300L,
                isDirectory = false,
                category = StorageCategory.AUDIO.name,
                parentPath = "/storage/emulated/0/Download",
                extension = "mp3"
            )
        )

        val tree = TreemapEngine.buildTree(files, "/storage/emulated/0")
        assertEquals("/storage/emulated/0", tree.path)
        assertEquals(1, tree.children.size)
        assertEquals("Download", tree.children[0].name)
        assertEquals(800L, tree.children[0].size)
    }

    @Test
    fun testEmptyTreemapLayout() {
        val laidOut = TreemapEngine.layoutSquarified(emptyList(), TreemapRect(0f, 0f, 500f, 500f))
        assertTrue(laidOut.isEmpty())
    }
}
