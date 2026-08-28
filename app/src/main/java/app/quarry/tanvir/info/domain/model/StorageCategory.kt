package app.quarry.tanvir.info.domain.model

import java.util.Locale

enum class StorageCategory(val displayName: String) {
    VIDEOS("Videos"),
    IMAGES("Images"),
    DOCUMENTS("Documents"),
    AUDIO("Audio"),
    ARCHIVES("Archives"),
    APKS("APKs"),
    OTHER("Other");

    companion object {
        private val VIDEO_EXTENSIONS = setOf(
            "mp4", "mkv", "mov", "avi", "webm", "3gp", "ts", "flv", "wmv", "m4v", "vob", "ogv", "f4v"
        )
        private val IMAGE_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "dng", "svg", "bmp", "ico", "raw", "nef", "cr2"
        )
        private val DOCUMENT_EXTENSIONS = setOf(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "epub", "mobi",
            "csv", "rtf", "md", "odt", "ods", "odp", "json", "xml", "html", "htm", "log"
        )
        private val AUDIO_EXTENSIONS = setOf(
            "mp3", "m4a", "flac", "wav", "ogg", "aac", "opus", "wma", "mid", "midi", "amr", "aiff"
        )
        private val ARCHIVE_EXTENSIONS = setOf(
            "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso", "tgz", "tbz2", "z", "lzma"
        )
        private val APK_EXTENSIONS = setOf(
            "apk", "xapk", "apkm", "apks"
        )

        fun fromExtension(ext: String): StorageCategory {
            val lower = ext.lowercase(Locale.ROOT).trimStart('.')
            return when {
                VIDEO_EXTENSIONS.contains(lower) -> VIDEOS
                IMAGE_EXTENSIONS.contains(lower) -> IMAGES
                DOCUMENT_EXTENSIONS.contains(lower) -> DOCUMENTS
                AUDIO_EXTENSIONS.contains(lower) -> AUDIO
                ARCHIVE_EXTENSIONS.contains(lower) -> ARCHIVES
                APK_EXTENSIONS.contains(lower) -> APKS
                else -> OTHER
            }
        }

        fun fromMimeType(mimeType: String?): StorageCategory? {
            if (mimeType == null) return null
            val lower = mimeType.lowercase(Locale.ROOT)
            return when {
                lower.startsWith("video/") -> VIDEOS
                lower.startsWith("image/") -> IMAGES
                lower.startsWith("audio/") -> AUDIO
                lower == "application/pdf" ||
                lower.contains("word") ||
                lower.contains("excel") ||
                lower.contains("powerpoint") ||
                lower.contains("document") ||
                lower.startsWith("text/") -> DOCUMENTS
                lower.contains("zip") ||
                lower.contains("tar") ||
                lower.contains("compressed") ||
                lower.contains("archive") -> ARCHIVES
                lower == "application/vnd.android.package-archive" -> APKS
                else -> null
            }
        }
    }
}

data class StorageItem(
    val id: Long = 0,
    val path: String,
    val name: String,
    val size: Long,
    val isDirectory: Boolean,
    val category: StorageCategory,
    val mimeType: String? = null,
    val lastModified: Long = 0,
    val parentPath: String? = null,
    val extension: String = "",
    val isScreenshot: Boolean = false,
    val isDownload: Boolean = false,
    val fileCount: Long = 0
)

object StorageFormatter {
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
        return if (digitGroups == 0) {
            String.format(Locale.US, "%d %s", bytes, units[digitGroups])
        } else {
            String.format(Locale.US, "%.1f %s", value, units[digitGroups])
        }
    }

    fun formatFileCount(count: Long): String {
        return String.format(Locale.US, "%,d files", count)
    }
}
