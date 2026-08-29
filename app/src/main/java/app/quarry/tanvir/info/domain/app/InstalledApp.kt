package app.quarry.tanvir.info.domain.app

/**
 * Size breakdown for an installed app.
 *
 * [apkBytes] is always available via the APK file length. [dataBytes] and
 * [cacheBytes] are null unless Usage Access has been granted, which gates
 * StorageStatsManager access.
 */
data class AppSizeInfo(
    val apkBytes: Long,
    val dataBytes: Long?,
    val cacheBytes: Long?
) {
    val totalBytes: Long
        get() = apkBytes + (dataBytes ?: 0L) + (cacheBytes ?: 0L)

    val isDetailed: Boolean
        get() = dataBytes != null && cacheBytes != null
}

data class InstalledApp(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val isSystemApp: Boolean,
    val isLaunchable: Boolean,
    val size: AppSizeInfo,
    val sourceDir: String
)
