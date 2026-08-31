package app.quarry.tanvir.info.domain.cleanup

import app.quarry.tanvir.info.data.database.FileEntity
import app.quarry.tanvir.info.domain.duplicates.FastDuplicateDetector
import app.quarry.tanvir.info.domain.model.StorageCategory
import app.quarry.tanvir.info.domain.model.StorageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class DefaultCleanupEngine(
    private val duplicateDetector: FastDuplicateDetector = FastDuplicateDetector()
) : CleanupEngine {

    override suspend fun getCandidates(items: List<StorageItem>): List<CleanupCandidateGroup> = withContext(Dispatchers.Default) {
        val nonDirItems = items.filter { !it.isDirectory }
        val now = System.currentTimeMillis()
        val sixMonthsAgo = now - (180L * 24 * 60 * 60 * 1000)
        val thirtyDaysAgo = now - (30L * 24 * 60 * 60 * 1000)

        val candidateGroups = mutableListOf<CleanupCandidateGroup>()

        // 1. Large Files (> 50 MB)
        val largeFiles = nonDirItems.filter { it.size >= 50 * 1024 * 1024L }.sortedByDescending { it.size }
        if (largeFiles.isNotEmpty()) {
            candidateGroups.add(
                CleanupCandidateGroup(
                    title = "Large Files",
                    description = "Files exceeding 50 MB consuming significant space",
                    items = largeFiles,
                    totalBytes = largeFiles.sumOf { it.size }
                )
            )
        }

        // 2. Old APK Install Packages
        val apks = nonDirItems.filter { it.category == StorageCategory.APKS }.sortedByDescending { it.size }
        if (apks.isNotEmpty()) {
            candidateGroups.add(
                CleanupCandidateGroup(
                    title = "Old APK Files",
                    description = "Installation packages you may no longer need",
                    items = apks,
                    totalBytes = apks.sumOf { it.size }
                )
            )
        }

        // 3. Screenshots
        val screenshots = nonDirItems.filter { it.isScreenshot || it.path.contains("screenshot", ignoreCase = true) }
            .sortedByDescending { it.lastModified }
        if (screenshots.isNotEmpty()) {
            candidateGroups.add(
                CleanupCandidateGroup(
                    title = "Screenshots",
                    description = "Captured screenshots that may be outdated",
                    items = screenshots,
                    totalBytes = screenshots.sumOf { it.size }
                )
            )
        }

        // 4. Old Downloads (> 30 days)
        val oldDownloads = nonDirItems.filter {
            (it.isDownload || it.path.contains("/Download/", ignoreCase = true)) &&
                    it.lastModified in 1..<thirtyDaysAgo
        }.sortedByDescending { it.size }
        if (oldDownloads.isNotEmpty()) {
            candidateGroups.add(
                CleanupCandidateGroup(
                    title = "Old Downloads",
                    description = "Files downloaded more than 30 days ago",
                    items = oldDownloads,
                    totalBytes = oldDownloads.sumOf { it.size }
                )
            )
        }

        // 5. Untouched Old Files (> 6 months)
        val oldFiles = nonDirItems.filter { it.lastModified in 1..sixMonthsAgo }.sortedByDescending { it.size }
        if (oldFiles.isNotEmpty()) {
            candidateGroups.add(
                CleanupCandidateGroup(
                    title = "Old & Untouched Files",
                    description = "Files not modified for over 6 months",
                    items = oldFiles,
                    totalBytes = oldFiles.sumOf { it.size }
                )
            )
        }

        // 6. Temporary & Log Files
        val tempExtensions = setOf("tmp", "temp", "log", "bak", "cache", "thumb")
        val potentialJunk = nonDirItems.filter { item ->
            val ext = item.extension.lowercase(Locale.ROOT)
            tempExtensions.contains(ext) || item.name.startsWith(".thumb")
        }.sortedByDescending { it.size }
        if (potentialJunk.isNotEmpty()) {
            candidateGroups.add(
                CleanupCandidateGroup(
                    title = "Temporary & Log Files",
                    description = "Residual log files, cache chunks, and temporary files",
                    items = potentialJunk,
                    totalBytes = potentialJunk.sumOf { it.size }
                )
            )
        }

        // 7. Empty Folders
        val emptyDirs = items.filter { it.isDirectory && it.fileCount == 0L }
        if (emptyDirs.isNotEmpty()) {
            candidateGroups.add(
                CleanupCandidateGroup(
                    title = "Empty Folders",
                    description = "Directories containing 0 files",
                    items = emptyDirs,
                    totalBytes = 0L
                )
            )
        }

        candidateGroups
    }

    suspend fun getCandidatesFromEntities(entities: List<FileEntity>): List<CleanupCandidateGroup> = withContext(Dispatchers.Default) {
        val items = entities.map { entity ->
            StorageItem(
                id = entity.id,
                path = entity.path,
                name = entity.name,
                size = entity.size,
                isDirectory = entity.isDirectory,
                category = StorageCategory.fromExtension(entity.extension),
                lastModified = entity.lastModified,
                parentPath = entity.parentPath,
                extension = entity.extension,
                isScreenshot = entity.isScreenshot,
                isDownload = entity.isDownload,
                fileCount = entity.directChildrenCount.toLong()
            )
        }
        getCandidates(items)
    }
}
