package app.quarry.tanvir.info.domain.file

import android.content.Context
import android.os.Environment
import app.quarry.tanvir.info.data.database.FileEntity
import app.quarry.tanvir.info.domain.model.StorageCategory
import app.quarry.tanvir.info.domain.scanner.ScanRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class TrashItem(
    val id: String,
    val originalPath: String,
    val trashPath: String,
    val name: String,
    val size: Long,
    val isDirectory: Boolean,
    val deletedTimestamp: Long
)

class TrashManager(
    private val context: Context,
    private val repository: ScanRepository
) {
    private val mutex = Mutex()

    /**
     * Primary external trash directory located at the root of primary shared storage.
     * Moving files here from anywhere in /storage/emulated/0 allows instantaneous atomic rename (O(1)).
     */
    private val externalTrashDir: File by lazy {
        val root = Environment.getExternalStorageDirectory()
        val dir = File(root, ".quarry_trash")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    /**
     * Legacy external trash directory used in older versions (inside Android/data sandbox).
     * Checked for backward compatibility so existing items can be restored/cleaned.
     */
    private val legacyExternalTrashDir: File? by lazy {
        try {
            val extFiles = context.getExternalFilesDir(null)
            if (extFiles != null) {
                val dir = File(extFiles, ".quarry_trash")
                if (dir.exists()) dir else null
            } else null
        } catch (ignored: Exception) {
            null
        }
    }

    /**
     * Internal fallback trash directory inside app private data storage.
     */
    private val internalTrashDir: File by lazy {
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

    /**
     * Selects the most optimal trash directory for a given file path to enable fast atomic renames.
     */
    private fun getTrashDirForPath(filePath: String): File {
        val externalRoot = Environment.getExternalStorageDirectory().absolutePath
        return if (filePath.startsWith(externalRoot) || filePath.startsWith("/storage/emulated/0")) {
            if (!externalTrashDir.exists()) externalTrashDir.mkdirs()
            externalTrashDir
        } else if (filePath.startsWith("/storage/")) {
            // Check if file is on a secondary SD card volume (e.g. /storage/ABCD-1234/...)
            val segments = filePath.split("/").filter { it.isNotEmpty() }
            if (segments.size >= 2) {
                val sdRoot = File("/storage/${segments[1]}")
                val sdTrash = File(sdRoot, ".quarry_trash")
                if (sdTrash.exists() || sdTrash.mkdirs()) {
                    return sdTrash
                }
            }
            if (!externalTrashDir.exists()) externalTrashDir.mkdirs()
            externalTrashDir
        } else {
            if (!internalTrashDir.exists()) internalTrashDir.mkdirs()
            internalTrashDir
        }
    }

    /**
     * Loads trash metadata safely, verifies physical file existence,
     * and discovers any orphaned files in trash directories.
     */
    fun loadMetadata() {
        try {
            val list = mutableListOf<TrashItem>()
            val knownTrashPaths = mutableSetOf<String>()

            if (metadataFile.exists()) {
                val jsonStr = metadataFile.readText()
                if (jsonStr.isNotBlank()) {
                    val array = JSONArray(jsonStr)
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val trashPath = obj.getString("trashPath")
                        val trashFile = File(trashPath)

                        if (trashFile.exists()) {
                            val item = TrashItem(
                                id = obj.getString("id"),
                                originalPath = obj.getString("originalPath"),
                                trashPath = trashPath,
                                name = obj.getString("name"),
                                size = if (obj.has("size") && obj.getLong("size") > 0) {
                                    obj.getLong("size")
                                } else {
                                    calculateFileSize(trashFile)
                                },
                                isDirectory = obj.optBoolean("isDirectory", trashFile.isDirectory),
                                deletedTimestamp = obj.optLong("deletedTimestamp", trashFile.lastModified())
                            )
                            list.add(item)
                            knownTrashPaths.add(trashFile.absolutePath)
                        }
                    }
                }
            }

            // Discover any orphan files present in all trash dirs not yet indexed in metadata
            discoverOrphanFiles(externalTrashDir, list, knownTrashPaths)
            legacyExternalTrashDir?.let { discoverOrphanFiles(it, list, knownTrashPaths) }
            discoverOrphanFiles(internalTrashDir, list, knownTrashPaths)

            list.sortByDescending { it.deletedTimestamp }
            _trashItems.value = list
        } catch (e: Exception) {
            _trashItems.value = emptyList()
        }
    }

    private fun discoverOrphanFiles(
        trashDir: File,
        list: MutableList<TrashItem>,
        knownPaths: MutableSet<String>
    ) {
        if (!trashDir.exists()) return
        val files = trashDir.listFiles() ?: return
        for (file in files) {
            if (!knownPaths.contains(file.absolutePath) && file.name != "trash_metadata.json") {
                val id = file.name
                val size = calculateFileSize(file)
                val originalPath = File(Environment.getExternalStorageDirectory(), file.name).absolutePath
                val item = TrashItem(
                    id = id,
                    originalPath = originalPath,
                    trashPath = file.absolutePath,
                    name = file.name,
                    size = size,
                    isDirectory = file.isDirectory,
                    deletedTimestamp = file.lastModified()
                )
                list.add(item)
                knownPaths.add(file.absolutePath)
            }
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
            // Atomic write via temporary file
            val tempFile = File(context.filesDir, "trash_metadata.tmp")
            tempFile.writeText(array.toString())
            if (tempFile.exists()) {
                if (metadataFile.exists()) metadataFile.delete()
                tempFile.renameTo(metadataFile)
            }
            _trashItems.value = items
        } catch (e: Exception) {
            // Fallback direct write
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
            } catch (ignored: Exception) {}
        }
    }

    /**
     * Moves a file or directory into Trash.
     */
    suspend fun moveToTrash(path: String): Result<TrashItem> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val source = File(path)
                if (!source.exists()) {
                    repository.deleteFileRecord(path)
                    return@withContext Result.failure(IllegalArgumentException("File not found on disk"))
                }

                val trashDir = getTrashDirForPath(path)
                val uniqueSuffix = UUID.randomUUID().toString().take(6)
                val trashId = "${System.currentTimeMillis()}_${uniqueSuffix}_${source.name}"
                val trashTarget = File(trashDir, trashId)

                val fileSize = calculateFileSize(source)
                val isDir = source.isDirectory

                // Try atomic filesystem rename first (instantaneous on same volume)
                var moved = source.renameTo(trashTarget)
                if (!moved) {
                    try {
                        java.nio.file.Files.move(source.toPath(), trashTarget.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                        moved = true
                    } catch (e: Exception) {
                        val copySuccess = if (isDir) {
                            source.copyRecursively(trashTarget, overwrite = true)
                        } else {
                            source.copyTo(trashTarget, overwrite = true)
                            true
                        }
                        if (copySuccess) {
                            if (isDir) source.deleteRecursively() else source.delete()
                            moved = true
                        }
                    }
                }

                if (!moved) {
                    return@withContext Result.failure(IllegalStateException("Could not move file to Trash"))
                }

                // Remove from database index
                repository.deleteFileRecord(path)

                val item = TrashItem(
                    id = trashId,
                    originalPath = path,
                    trashPath = trashTarget.absolutePath,
                    name = source.name,
                    size = fileSize,
                    isDirectory = isDir,
                    deletedTimestamp = System.currentTimeMillis()
                )

                val updated = _trashItems.value + item
                saveMetadata(updated)
                Result.success(item)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Moves multiple files or directories into Trash in batch.
     */
    suspend fun moveToTrashBatch(paths: List<String>): Map<String, Boolean> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val results = mutableMapOf<String, Boolean>()
            val currentList = _trashItems.value.toMutableList()
            val entitiesToDelete = mutableListOf<String>()

            for (path in paths) {
                try {
                    val source = File(path)
                    if (!source.exists()) {
                        entitiesToDelete.add(path)
                        results[path] = false
                        continue
                    }

                    val trashDir = getTrashDirForPath(path)
                    val uniqueSuffix = UUID.randomUUID().toString().take(6)
                    val trashId = "${System.currentTimeMillis()}_${uniqueSuffix}_${source.name}"
                    val trashTarget = File(trashDir, trashId)

                    val fileSize = calculateFileSize(source)
                    val isDir = source.isDirectory

                    var moved = source.renameTo(trashTarget)
                    if (!moved) {
                        try {
                            java.nio.file.Files.move(source.toPath(), trashTarget.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                            moved = true
                        } catch (e: Exception) {
                            val copySuccess = if (isDir) {
                                source.copyRecursively(trashTarget, overwrite = true)
                            } else {
                                source.copyTo(trashTarget, overwrite = true)
                                true
                            }
                            if (copySuccess) {
                                if (isDir) source.deleteRecursively() else source.delete()
                                moved = true
                            }
                        }
                    }

                    if (moved) {
                        entitiesToDelete.add(path)
                        val item = TrashItem(
                            id = trashId,
                            originalPath = path,
                            trashPath = trashTarget.absolutePath,
                            name = source.name,
                            size = fileSize,
                            isDirectory = isDir,
                            deletedTimestamp = System.currentTimeMillis()
                        )
                        currentList.add(item)
                        results[path] = true
                    } else {
                        results[path] = false
                    }
                } catch (e: Exception) {
                    results[path] = false
                }
            }

            for (deletedPath in entitiesToDelete) {
                repository.deleteFileRecord(deletedPath)
            }
            saveMetadata(currentList)
            results
        }
    }

    /**
     * Restores an item from Trash back to its original path (or safe non-colliding variant).
     * Automatically inserts restored files back into the Room database.
     */
    suspend fun restoreItem(trashId: String): Result<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val currentList = _trashItems.value
            val item = currentList.find { it.id == trashId }
                ?: return@withContext Result.failure(IllegalArgumentException("Trash item not found"))

            try {
                val trashFile = File(item.trashPath)
                if (!trashFile.exists()) {
                    val updated = currentList.filter { it.id != trashId }
                    saveMetadata(updated)
                    return@withContext Result.failure(IllegalStateException("Trash file no longer exists on disk"))
                }

                var targetFile = File(item.originalPath)
                // If original target already exists, resolve collision to prevent overwriting
                if (targetFile.exists()) {
                    targetFile = resolveDestinationCollision(targetFile)
                }

                targetFile.parentFile?.let {
                    if (!it.exists()) it.mkdirs()
                }

                var restored = trashFile.renameTo(targetFile)
                if (!restored) {
                    try {
                        java.nio.file.Files.move(trashFile.toPath(), targetFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                        restored = true
                    } catch (e: Exception) {
                        val copied = if (trashFile.isDirectory) {
                            trashFile.copyRecursively(targetFile, overwrite = true)
                        } else {
                            trashFile.copyTo(targetFile, overwrite = true)
                            true
                        }

                        if (copied) {
                            if (trashFile.isDirectory) trashFile.deleteRecursively() else trashFile.delete()
                            restored = true
                        }
                    }
                }

                if (!restored) {
                    return@withContext Result.failure(IllegalStateException("Failed to restore file"))
                }

                // Update metadata
                val updated = currentList.filter { it.id != trashId }
                saveMetadata(updated)

                // Re-index into Room database
                val entities = mutableListOf<FileEntity>()
                if (targetFile.isDirectory) {
                    indexDirectoryRecursively(targetFile, entities)
                } else {
                    val category = StorageCategory.fromExtension(targetFile.extension)
                    entities.add(
                        FileEntity(
                            path = targetFile.absolutePath,
                            name = targetFile.name,
                            size = targetFile.length(),
                            isDirectory = false,
                            category = category.name,
                            lastModified = targetFile.lastModified(),
                            parentPath = targetFile.parentFile?.absolutePath,
                            extension = targetFile.extension
                        )
                    )
                }

                if (entities.isNotEmpty()) {
                    repository.insertFiles(entities)
                }

                Result.success(targetFile.absolutePath)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Restores multiple items from Trash in batch with atomic efficiency.
     */
    suspend fun restoreItemsBatch(trashIds: List<String>): Map<String, Boolean> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val results = mutableMapOf<String, Boolean>()
            var currentList = _trashItems.value.toList()
            val restoredEntities = mutableListOf<FileEntity>()
            val idsToRemove = mutableSetOf<String>()

            for (trashId in trashIds) {
                val item = currentList.find { it.id == trashId }
                if (item == null) {
                    results[trashId] = false
                    continue
                }

                try {
                    val trashFile = File(item.trashPath)
                    if (!trashFile.exists()) {
                        idsToRemove.add(trashId)
                        results[trashId] = false
                        continue
                    }

                    var targetFile = File(item.originalPath)
                    if (targetFile.exists()) {
                        targetFile = resolveDestinationCollision(targetFile)
                    }

                    targetFile.parentFile?.let {
                        if (!it.exists()) it.mkdirs()
                    }

                    var restored = trashFile.renameTo(targetFile)
                    if (!restored) {
                        try {
                            java.nio.file.Files.move(trashFile.toPath(), targetFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                            restored = true
                        } catch (e: Exception) {
                            val copied = if (trashFile.isDirectory) {
                                trashFile.copyRecursively(targetFile, overwrite = true)
                            } else {
                                trashFile.copyTo(targetFile, overwrite = true)
                                true
                            }
                            if (copied) {
                                if (trashFile.isDirectory) trashFile.deleteRecursively() else trashFile.delete()
                                restored = true
                            }
                        }
                    }

                    if (restored) {
                        idsToRemove.add(trashId)
                        results[trashId] = true

                        if (targetFile.isDirectory) {
                            indexDirectoryRecursively(targetFile, restoredEntities)
                        } else {
                            val category = StorageCategory.fromExtension(targetFile.extension)
                            restoredEntities.add(
                                FileEntity(
                                    path = targetFile.absolutePath,
                                    name = targetFile.name,
                                    size = targetFile.length(),
                                    isDirectory = false,
                                    category = category.name,
                                    lastModified = targetFile.lastModified(),
                                    parentPath = targetFile.parentFile?.absolutePath,
                                    extension = targetFile.extension
                                )
                            )
                        }
                    } else {
                        results[trashId] = false
                    }
                } catch (e: Exception) {
                    results[trashId] = false
                }
            }

            if (idsToRemove.isNotEmpty()) {
                currentList = currentList.filter { !idsToRemove.contains(it.id) }
                saveMetadata(currentList)
            }
            if (restoredEntities.isNotEmpty()) {
                repository.insertFiles(restoredEntities)
            }
            results
        }
    }

    /**
     * Permanently deletes an item from Trash and filesystem.
     */
    suspend fun deletePermanently(trashId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        mutex.withLock {
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
    }

    /**
     * Permanently deletes multiple items from Trash in batch.
     */
    suspend fun deletePermanentlyBatch(trashIds: List<String>): Map<String, Boolean> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val results = mutableMapOf<String, Boolean>()
            var currentList = _trashItems.value.toList()
            val idsToRemove = mutableSetOf<String>()

            for (trashId in trashIds) {
                val item = currentList.find { it.id == trashId }
                if (item == null) {
                    results[trashId] = false
                    continue
                }
                try {
                    val trashFile = File(item.trashPath)
                    if (trashFile.exists()) {
                        if (trashFile.isDirectory) {
                            trashFile.deleteRecursively()
                        } else {
                            trashFile.delete()
                        }
                    }
                    idsToRemove.add(trashId)
                    results[trashId] = true
                } catch (e: Exception) {
                    results[trashId] = false
                }
            }

            if (idsToRemove.isNotEmpty()) {
                currentList = currentList.filter { !idsToRemove.contains(it.id) }
                saveMetadata(currentList)
            }
            results
        }
    }

    /**
     * Empties all items currently in Trash.
     */
    suspend fun emptyTrash(): Result<Int> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val currentList = _trashItems.value
            var deletedCount = 0
            for (item in currentList) {
                val file = File(item.trashPath)
                if (file.exists()) {
                    if (file.isDirectory) file.deleteRecursively() else file.delete()
                }
                deletedCount++
            }

            // Also clean any unindexed residue in all trash folders
            cleanFolderContents(externalTrashDir)
            legacyExternalTrashDir?.let { cleanFolderContents(it) }
            cleanFolderContents(internalTrashDir)

            saveMetadata(emptyList())
            Result.success(deletedCount)
        }
    }

    /**
     * Automatically purges items in trash older than maxAgeDays (default: 30 days).
     */
    suspend fun purgeExpiredItems(maxAgeDays: Int = 30): Result<Int> = withContext(Dispatchers.IO) {
        val cutoffTimestamp = System.currentTimeMillis() - (maxAgeDays.toLong() * 24 * 60 * 60 * 1000)
        val expiredIds = _trashItems.value
            .filter { it.deletedTimestamp < cutoffTimestamp }
            .map { it.id }

        if (expiredIds.isEmpty()) return@withContext Result.success(0)

        var count = 0
        for (id in expiredIds) {
            if (deletePermanently(id).isSuccess) {
                count++
            }
        }
        Result.success(count)
    }

    private fun cleanFolderContents(folder: File) {
        if (!folder.exists()) return
        val files = folder.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        }
    }

    private fun calculateFileSize(file: File): Long {
        return if (file.isDirectory) {
            file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } else {
            file.length()
        }
    }

    private fun resolveDestinationCollision(target: File): File {
        val parent = target.parentFile ?: return target
        val nameWithoutExt = target.nameWithoutExtension
        val ext = target.extension

        var counter = 1
        var candidate: File
        do {
            val newName = if (ext.isNotEmpty()) {
                "$nameWithoutExt (Restored $counter).$ext"
            } else {
                "$nameWithoutExt (Restored $counter)"
            }
            candidate = File(parent, newName)
            counter++
        } while (candidate.exists())

        return candidate
    }

    private fun indexDirectoryRecursively(directory: File, list: MutableList<FileEntity>) {
        val children = directory.listFiles() ?: return
        for (child in children) {
            val category = StorageCategory.fromExtension(child.extension)
            val entity = FileEntity(
                path = child.absolutePath,
                name = child.name,
                size = if (child.isDirectory) calculateFileSize(child) else child.length(),
                isDirectory = child.isDirectory,
                category = category.name,
                lastModified = child.lastModified(),
                parentPath = directory.absolutePath,
                extension = child.extension
            )
            list.add(entity)
            if (child.isDirectory) {
                indexDirectoryRecursively(child, list)
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: TrashManager? = null

        fun getInstance(context: Context, repository: ScanRepository): TrashManager {
            return INSTANCE ?: synchronized(this) {
                val instance = TrashManager(context.applicationContext, repository)
                INSTANCE = instance
                instance
            }
        }
    }
}
