package app.quarry.tanvir.info.domain.cleanup

import app.quarry.tanvir.info.domain.model.StorageItem

data class CleanupCandidateGroup(
    val title: String,
    val description: String,
    val items: List<StorageItem>,
    val totalBytes: Long
)

interface CleanupEngine {
    suspend fun getCandidates(items: List<StorageItem>): List<CleanupCandidateGroup>
}
