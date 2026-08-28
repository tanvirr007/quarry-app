package app.quarry.tanvir.info.domain.file

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import app.quarry.tanvir.info.data.database.FileEntity
import app.quarry.tanvir.info.domain.model.StorageCategory
import app.quarry.tanvir.info.domain.scanner.ScanRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class TrashItem(
    val id: String,
    val originalPath: String,
    val trashPath: String,
    val name: String,
    val size: Long,
    val isDirectory: Boolean,
    val deletedTimestamp: Long
)

class FileOperationsManager(
    private val context: Context,
    private val repository: ScanRepository
) {
    private val trashDirectory: File by lazy {
        File(context.filesDir, ".quarry_trash").apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Renames a single file or directory and updates the database record.
     */
    suspend fun renameFile(oldPath: String, newName: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(oldPath)
            if (!sourceFile.exists()) {
                return@withContext Result.failure(IllegalArgumentException("Source file does not exist"))
            }

            val parentDir = sourceFile.parentFile ?: return@withContext Result.failure(IllegalArgumentException("Parent folder cannot be resolved"))
            val targetFile = File(parentDir, newName.trim())

            if (targetFile.exists()) {
                return@withContext Result.failure(IllegalArgumentException("A file with this name already exists"))
            }

            val renamed = sourceFile.renameTo(targetFile)
            if (!renamed) {
                return@withContext Result.failure(IllegalStateException("Could not rename file. Check filesystem permissions."))
            }

            // Update database
            repository.deleteFileRecord(oldPath)
            val extension = targetFile.extension
            val category = StorageCategory.fromExtension(extension)
            val updatedEntity = FileEntity(
                path = targetFile.absolutePath,
                name = targetFile.name,
                size = targetFile.length(),
                isDirectory = targetFile.isDirectory,
                category = category.name,
                lastModified = targetFile.lastModified(),
                parentPath = parentDir.absolutePath,
                extension = extension
            )
            // Rescan directory / re-insert entity
            repository.getChildren(parentDir.absolutePath)
            Result.success(targetFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Moves a file or directory to Quarry's secure local trash.
     */
    suspend fun moveToTrash(path: String): Result<TrashItem> = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (!file.exists()) {
                repository.deleteFileRecord(path)
                return@withContext Result.failure(IllegalArgumentException("File does not exist"))
            }

            val trashId = "${System.currentTimeMillis()}_${file.name}"
            val trashTarget = File(trashDirectory, trashId)

            val moved = file.renameTo(trashTarget)
            if (!moved) {
                // Fallback to copy and delete
                file.copyRecursively(trashTarget, overwrite = true)
                file.deleteRecursively()
            }

            repository.deleteFileRecord(path)

            val trashItem = TrashItem(
                id = trashId,
                originalPath = path,
                trashPath = trashTarget.absolutePath,
                name = file.name,
                size = trashTarget.length(),
                isDirectory = trashTarget.isDirectory,
                deletedTimestamp = System.currentTimeMillis()
            )
            Result.success(trashItem)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Permanently deletes a file or directory from the filesystem and database.
     */
    suspend fun deletePermanently(path: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            var deleted = true
            if (file.exists()) {
                deleted = if (file.isDirectory) {
                    file.deleteRecursively()
                } else {
                    file.delete()
                }
            }
            repository.deleteFileRecord(path)
            if (deleted) {
                Result.success(true)
            } else {
                Result.failure(IllegalStateException("Failed to delete file from disk"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Bulk permanently deletes a list of files.
     */
    suspend fun bulkDelete(paths: List<String>): Map<String, Boolean> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, Boolean>()
        for (path in paths) {
            val res = deletePermanently(path)
            results[path] = res.isSuccess
        }
        results
    }

    /**
     * Opens a file using the system Default Intent.
     */
    fun openFile(filePath: String) {
        try {
            val file = File(filePath)
            if (!file.exists()) return

            val uri = getUriForFile(file)
            val mimeType = getMimeType(file)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Log or show toast
        }
    }

    /**
     * Shares a file using Android's system share sheet.
     */
    fun shareFile(filePath: String) {
        try {
            val file = File(filePath)
            if (!file.exists()) return

            val uri = getUriForFile(file)
            val mimeType = getMimeType(file)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Share file").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            // Handle share failure
        }
    }

    private fun getUriForFile(file: File): Uri {
        return try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            Uri.fromFile(file)
        }
    }

    private fun getMimeType(file: File): String {
        val extension = file.extension.lowercase(Locale.ROOT)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
    }
}
