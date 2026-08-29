package app.quarry.tanvir.info.domain.app

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.os.storage.StorageManager
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Discovers and manages installed apps.
 *
 * App listing uses `MAIN`/`LAUNCHER` intent queries (Play-policy safe, no
 * QUERY_ALL_PACKAGES). APK size is always resolvable; data/cache sizes require
 * Usage Access via StorageStatsManager.
 */
class AppManager(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

    fun hasUsageAccess(): Boolean {
        try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
            if (mode == AppOpsManager.MODE_ALLOWED) return true

            // Fallback: Check if StorageStatsManager can query stats for our UID without SecurityException
            val statsManager =
                context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
            if (statsManager != null) {
                statsManager.queryStatsForUid(StorageManager.UUID_DEFAULT, Process.myUid())
                return true
            }
        } catch (e: SecurityException) {
            return false
        } catch (e: Exception) {
            // Non-security errors indicate the permission itself was granted
            return true
        }
        return false
    }

    suspend fun getInstalledApps(includeSystemApps: Boolean = true): List<InstalledApp> =
        withContext(Dispatchers.IO) {
            val launchablePackages = queryLaunchablePackages()
            val all = mutableListOf<InstalledApp>()

            launchablePackages.forEach { packageName ->
                val app = buildApp(packageName) ?: return@forEach
                if (app.isSystemApp && !includeSystemApps) return@forEach
                all += app
            }

            all.sortedByDescending { it.size.totalBytes }
        }

    fun openApp(packageName: String): Boolean {
        return try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun openAppDetails(packageName: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Details intent is widely supported; ignore rare failures.
        }
    }

    fun uninstall(packageName: String) {
        try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:$packageName")
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                    data = Uri.parse("package:$packageName")
                    putExtra(Intent.EXTRA_RETURN_RESULT, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            } catch (e2: Exception) {
                openAppDetails(packageName)
            }
        }
    }

    fun openUsageAccessSettings() {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fall back to app details settings if usage access screen is unavailable.
            openAppDetails(context.packageName)
        }
    }

    private fun queryLaunchablePackages(): List<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = try {
            packageManager.queryIntentActivities(intent, 0)
        } catch (e: Exception) {
            emptyList()
        }
        return resolveInfos.mapNotNull { it.activityInfo?.packageName }.distinct()
    }

    private fun buildApp(packageName: String): InstalledApp? {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val label = packageManager.getApplicationLabel(appInfo).toString()
            val versionName = try {
                packageManager.getPackageInfo(packageName, 0).versionName
            } catch (e: Exception) {
                null
            }
            val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            val isLaunchable = packageManager.getLaunchIntentForPackage(packageName) != null

            InstalledApp(
                packageName = packageName,
                label = label,
                versionName = versionName,
                isSystemApp = isSystemApp,
                isLaunchable = isLaunchable,
                size = resolveSize(appInfo),
                sourceDir = appInfo.sourceDir.orEmpty()
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun resolveSize(appInfo: ApplicationInfo): AppSizeInfo {
        val apkBytes = try {
            File(appInfo.sourceDir).length()
        } catch (e: Exception) {
            0L
        }

        if (!hasUsageAccess()) {
            return AppSizeInfo(apkBytes = apkBytes, dataBytes = null, cacheBytes = null)
        }

        return try {
            val statsManager =
                context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
            // queryStatsForUid is available since API 26 (safe for minSdk 31).
            val stats = statsManager.queryStatsForUid(StorageManager.UUID_DEFAULT, appInfo.uid)
            AppSizeInfo(
                apkBytes = apkBytes,
                dataBytes = stats.dataBytes,
                cacheBytes = stats.cacheBytes
            )
        } catch (e: Exception) {
            AppSizeInfo(apkBytes = apkBytes, dataBytes = null, cacheBytes = null)
        }
    }
}
