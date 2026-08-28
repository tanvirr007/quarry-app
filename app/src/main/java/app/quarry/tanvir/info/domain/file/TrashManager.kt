package app.quarry.tanvir.info.domain.file

import android.content.Context
import app.quarry.tanvir.info.data.database.FileEntity
import app.quarry.tanvir.info.domain.model.StorageCategory
import app.quarry.tanvir.info.domain.scanner.ScanRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class TrashManager(
    private val context: Context,
    private val repository: ScanRepository
) {
    private val trashDir: File by lazy {
        File(context.filesDir, ".quarry_trash").apply {
            if (!exists()) mkdirs()
        }
    }

    private val metadataFile: File by lazy {
        File(context.filesDir, "trash_metadata.json")
    }

    private val _trashItems = MutableStateFlow<List<TrashItem>>(emptyList())
    val trashItems: StateFlow<List<TrashItem>> = _trashItems.asStateFlow()

    init {
        loadMetadata()
    }

    private fun loadMetadata() {
        if (!metadataFile.exists()) {
            _trashItems.value = emptyList()
            return
        }

        try {
            val jsonStr = metadataFile.readText()
            val array = JSONArray(jsonStr)
            val list = mutableListOf<TrashItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val item = TrashItem(
                    id = obj.getString("id"),
                    originalPath = obj.getString("originalPath"),
                    trashPath = obj.getString("trashPath"),
                    name = obj.getString("name"),
                    size = obj.getLong("size"),
                    isDirectory = obj.getBoolean("isDirectory"),
                    deletedTimestamp = obj.getLong("deletedTimestamp")
                )
                // Verify trash file still exists
                if (File(item.trashPath).exists()) {
                    list.add(item)
                }
            }
            _trashItems.value = list
        } catch (e: Exception) {
            _trashItems.value = emptyList()
        }
    }

    private fun saveMetadata(items: List<TrashItem>) {
        try {
            val array = JSONArray()
            for (item in items) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("originalPath", item.originalPath)
                    put("trashPath", item.trashPath)
                    put("name", item.name)
                    put("size", item.size)
                    put("isDirectory", item.isDirectory)
                    put("deletedTimestamp", item.deletedTimestamp)
                }
                array.put(obj)
            }
            metadataFile.writeText(array.toString())
            _trashItems.value = items
        } catch (e: Exception) {
            // Ignore write error
        }
    }

    suspend fun moveToTrash(path: String): Result<TrashItem> = withContext(Dispatchers.IO) {
        try {
            val source = File(path)
            if (!source.exists()) {
                repository.deleteFileRecord(path)
                return@withContext Result.failure(IllegalArgumentException("File not found on disk"))
            }

            val trashId = "${System.currentTimeMillis()}_${source.name}"
            val trashTarget = File(trashDir, trashId)

            val moved = source.renameTo(trashTarget)
            if (!moved) {
                source.copyRecursively(trashTarget, overwrite = true)
                source.deleteRecursively()
            }

            repository.deleteFileRecord(path)

            val item = TrashItem(
                id = trashId,
                originalPath = path,
                trashPath = trashTarget.absolutePath,
                name = source.name,
                size = trashTarget.length(),
                isDirectory = trashTarget.isDirectory,
                deletedTimestamp = System.currentTimeMillis()
            )

            val updated = _trashItems.value + item
            saveMetadata(updated)
            Result.success(item)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreItem(trashId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val currentList = _trashItems.value
        val item = currentList.find { it.id == trashId }
            ?: return@withContext Result.failure(IllegalArgumentException("Trash item not found"))

        try {
            val trashFile = File(item.trashPath)
            val originalTarget = File(item.originalPath)

            originalTarget.parentFile?.let { if (!it.exists()) it.mkdirs() }

            val restored = trashFile.renameTo(originalTarget)
            if (!restored) {
                trashFile.copyRecursively(originalTarget, overwrite = true)
                trashFile.deleteRecursively()
            }

            // Update metadata
            val updated = currentList.filter { it.id != trashId }
            saveMetadata(updated)

            // Re-insert into database
            val category = StorageCategory.fromExtension(originalTarget.extension)
            val entity = FileEntity(
                path = originalTarget.absolutePath,
                name = originalTarget.name,
                size = originalTarget.length(),
                isDirectory = originalTarget.isDirectory,
                category = category.name,
                lastModified = originalTarget.lastModified(),
                parentPath = originalTarget.parentFile?.absolutePath,
                extension = originalTarget.extension
            )
            repository.getChildren(originalTarget.parentFile?.absolutePath ?: "")
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deletePermanently(trashId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val currentList = _trashItems.value
        val item = currentList.find { it.id == trashId }
            ?: return@withContext Result.failure(IllegalArgumentException("Trash item not found"))

        try {
            val trashFile = File(item.trashPath)
            if (trashFile.exists()) {
                if (trashFile.isDirectory) {
                    trashFile.deleteRecursively()
                } else {
                    trashFile.delete()
                }
            }
            val updated = currentList.filter { it.id != trashId }
            saveMetadata(updated)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun emptyTrash(): Result<Int> = withContext(Dispatchers.IO) {
        val currentList = _trashItems.value
        var deletedCount = 0
        for (item in currentList) {
            val file = File(item.trashPath)
            if (file.exists()) {
                if (file.isDirectory) file.deleteRecursively() else file.delete()
            }
            deletedCount++
        }
        saveMetadata(emptyList())
        Result.success(deletedCount)
    }
}
