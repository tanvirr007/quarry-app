package app.quarry.tanvir.info.domain.file

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import app.quarry.tanvir.info.data.database.FileEntity
import app.quarry.tanvir.info.domain.model.StorageCategory
import app.quarry.tanvir.info.domain.scanner.ScanRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class FileOperationsManager(
    private val context: Context,
    private val repository: ScanRepository
) {
    private val trashManager = TrashManager.getInstance(context, repository)

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
            repository.insertFile(updatedEntity)
            Result.success(targetFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Moves a file or directory to Quarry's secure trash bin.
     */
    suspend fun moveToTrash(path: String): Result<TrashItem> = withContext(Dispatchers.IO) {
        trashManager.moveToTrash(path)
    }

    /**
     * Bulk moves a list of files or directories to Trash.
     */
    suspend fun bulkMoveToTrash(paths: List<String>): Map<String, Boolean> = withContext(Dispatchers.IO) {
        trashManager.moveToTrashBatch(paths)
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
     * Bulk permanently deletes a list of files with batch database synchronization.
     */
    suspend fun bulkDelete(paths: List<String>): Map<String, Boolean> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, Boolean>()
        val deletedPaths = mutableListOf<String>()

        for (path in paths) {
            try {
                val file = File(path)
                val deleted = if (!file.exists()) {
                    true
                } else if (file.isDirectory) {
                    file.deleteRecursively()
                } else {
                    file.delete()
                }

                if (deleted) {
                    deletedPaths.add(path)
                    results[path] = true
                } else {
                    results[path] = false
                }
            } catch (e: Exception) {
                results[path] = false
            }
        }

        if (deletedPaths.isNotEmpty()) {
            repository.deleteFileRecords(deletedPaths)
        }
        results
    }

    /**
     * Opens a file using the system Default Intent with support for APK package installers.
     */
    fun openFile(filePath: String) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                showToast("File does not exist")
                return
            }

            val uri = getUriForFile(file)
            val extension = file.extension.lowercase(Locale.ROOT)
            val mimeType = getMimeType(file)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            try {
                val file = File(filePath)
                val uri = getUriForFile(file)
                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val chooser = Intent.createChooser(fallbackIntent, "Open with").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            } catch (ex: Exception) {
                showToast("No application found to open this file")
            }
        } catch (e: Exception) {
            showToast("Failed to open file: ${e.localizedMessage}")
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
            showToast("Failed to share file")
        }
    }

    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
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
        return when (extension) {
            "apk", "xapk", "apkm", "apks" -> "application/vnd.android.package-archive"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
        }
    }
}
