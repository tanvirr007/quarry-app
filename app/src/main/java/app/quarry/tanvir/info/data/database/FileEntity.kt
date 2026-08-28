package app.quarry.tanvir.info.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "files",
    indices = [
        Index(value = ["path"], unique = true),
        Index(value = ["parentPath"]),
        Index(value = ["category"]),
        Index(value = ["size"]),
        Index(value = ["lastModified"]),
        Index(value = ["extension"]),
        Index(value = ["isDirectory"])
    ]
)
data class FileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val path: String,
    val name: String,
    val size: Long,
    val isDirectory: Boolean,
    val category: String,
    val mimeType: String? = null,
    val lastModified: Long = 0,
    val parentPath: String? = null,
    val extension: String = "",
    val isScreenshot: Boolean = false,
    val isDownload: Boolean = false,
    val directChildrenCount: Int = 0,
    val partialHash: String? = null,
    val fullHash: String? = null
)

data class CategoryStat(
    val category: String,
    val totalBytes: Long,
    val fileCount: Long
)

data class FolderStat(
    val path: String,
    val name: String,
    val totalBytes: Long,
    val fileCount: Long
)
