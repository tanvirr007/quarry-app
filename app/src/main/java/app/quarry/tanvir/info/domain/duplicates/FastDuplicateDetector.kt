package app.quarry.tanvir.info.domain.duplicates

import app.quarry.tanvir.info.data.database.FileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

class FastDuplicateDetector : DuplicateDetector {

    companion object {
        private const val PARTIAL_HASH_SIZE_BYTES = 8192 // 8 KB
    }

    override suspend fun findDuplicates(items: List<app.quarry.tanvir.info.domain.model.StorageItem>): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        // Convert to FileEntity equivalents or filter non-directories
        val validItems = items.filter { !it.isDirectory && it.size > 0 }
        if (validItems.size < 2) return@withContext emptyList()

        // Stage 1: Size grouping
        val sizeGroups = validItems.groupBy { it.size }.filter { it.value.size > 1 }
        if (sizeGroups.isEmpty()) return@withContext emptyList()

        val confirmedDuplicateGroups = mutableListOf<DuplicateGroup>()

        for ((size, candidateFiles) in sizeGroups) {
            // Stage 2: Partial 8KB Hash
            val partialHashGroups = candidateFiles.groupBy { item ->
                computePartialHash(item.path)
            }.filter { it.value.size > 1 && it.key.isNotEmpty() }

            for ((_, partialCandidates) in partialHashGroups) {
                // Stage 3: Full SHA-256 Hash
                val fullHashGroups = partialCandidates.groupBy { item ->
                    computeFullHash(item.path)
                }.filter { it.value.size > 1 && it.key.isNotEmpty() }

                for ((hash, duplicates) in fullHashGroups) {
                    confirmedDuplicateGroups.add(
                        DuplicateGroup(
                            size = size,
                            items = duplicates,
                            hash = hash
                        )
                    )
                }
            }
        }

        confirmedDuplicateGroups.sortedByDescending { it.recoverableBytes }
    }

    suspend fun findDuplicatesFromEntities(files: List<FileEntity>): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        val nonDirFiles = files.filter { !it.isDirectory && it.size > 0 }
        if (nonDirFiles.size < 2) return@withContext emptyList()

        // Stage 1: Group by identical file size
        val sizeGroups = nonDirFiles.groupBy { it.size }.filter { it.value.size > 1 }
        if (sizeGroups.isEmpty()) return@withContext emptyList()

        val confirmedGroups = mutableListOf<DuplicateGroup>()

        for ((size, candidateFiles) in sizeGroups) {
            // Stage 2: Partial 8KB Hash
            val partialHashGroups = candidateFiles.groupBy { entity ->
                computePartialHash(entity.path)
            }.filter { it.value.size > 1 && it.key.isNotEmpty() }

            for ((_, partialCandidates) in partialHashGroups) {
                // Stage 3: Full SHA-256 Hash
                val fullHashGroups = partialCandidates.groupBy { entity ->
                    computeFullHash(entity.path)
                }.filter { it.value.size > 1 && it.key.isNotEmpty() }

                for ((hash, duplicates) in fullHashGroups) {
                    val storageItems = duplicates.map { entity ->
                        app.quarry.tanvir.info.domain.model.StorageItem(
                            id = entity.id,
                            path = entity.path,
                            name = entity.name,
                            size = entity.size,
                            isDirectory = entity.isDirectory,
                            category = app.quarry.tanvir.info.domain.model.StorageCategory.fromExtension(entity.extension),
                            lastModified = entity.lastModified,
                            parentPath = entity.parentPath,
                            extension = entity.extension
                        )
                    }

                    confirmedGroups.add(
                        DuplicateGroup(
                            size = size,
                            items = storageItems,
                            hash = hash
                        )
                    )
                }
            }
        }

        confirmedGroups.sortedByDescending { it.recoverableBytes }
    }

    private fun computePartialHash(filePath: String): String {
        return try {
            val file = File(filePath)
            if (!file.exists() || !file.canRead()) return ""

            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(PARTIAL_HASH_SIZE_BYTES)
            FileInputStream(file).use { stream ->
                val read = stream.read(buffer, 0, PARTIAL_HASH_SIZE_BYTES)
                if (read > 0) {
                    digest.update(buffer, 0, read)
                }
            }
            bytesToHex(digest.digest())
        } catch (e: Exception) {
            ""
        }
    }

    private fun computeFullHash(filePath: String): String {
        return try {
            val file = File(filePath)
            if (!file.exists() || !file.canRead()) return ""

            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(65536) // 64 KB chunk
            FileInputStream(file).use { stream ->
                var bytesRead: Int
                while (stream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            bytesToHex(digest.digest())
        } catch (e: Exception) {
            ""
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 2] = HEX_ARRAY[v ushr 4]
            hexChars[i * 2 + 1] = HEX_ARRAY[v and 0x0F]
        }
        return String(hexChars)
    }
}

private val HEX_ARRAY = "0123456789abcdef".toCharArray()
