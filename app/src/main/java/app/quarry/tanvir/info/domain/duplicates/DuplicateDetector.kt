package app.quarry.tanvir.info.domain.duplicates

import app.quarry.tanvir.info.domain.model.StorageItem

data class DuplicateGroup(
    val size: Long,
    val items: List<StorageItem>,
    val hash: String = ""
) {
    val recoverableBytes: Long get() = if (items.size > 1) (items.size - 1) * size else 0L
}

interface DuplicateDetector {
    suspend fun findDuplicates(items: List<StorageItem>): List<DuplicateGroup>
}
