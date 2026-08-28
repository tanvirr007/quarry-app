package app.quarry.tanvir.info.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.quarry.tanvir.info.R
import app.quarry.tanvir.info.domain.model.StorageFormatter
import app.quarry.tanvir.info.domain.scanner.ScanProgress
import app.quarry.tanvir.info.domain.scanner.ScanRepository
import app.quarry.tanvir.info.domain.scanner.ScanState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class ScanWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        createNotificationChannel()

        val initialNotification = createNotification(
            progressText = "Starting storage scan…",
            progressPercent = 0,
            isIndeterminate = true
        )

        val foregroundInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, initialNotification)
        }

        try {
            setForeground(foregroundInfo)
        } catch (e: Exception) {
            // Foreground may not be available in some scenarios; continue execution
        }

        val repository = ScanRepository.getInstance(appContext)
        repository.startScan()

        var isDone = false
        var isSuccess = true

        repository.scanState.collectLatest { state ->
            when (state) {
                is ScanState.Scanning -> {
                    val progress = state.progress
                    val percent = (progress.progressFraction * 100).toInt()
                    val text = "${StorageFormatter.formatFileCount(progress.filesScanned)} (${StorageFormatter.formatBytes(progress.bytesScanned)})"

                    notificationManager.notify(
                        NOTIFICATION_ID,
                        createNotification(
                            progressText = text,
                            progressPercent = percent,
                            isIndeterminate = percent <= 0
                        )
                    )
                }
                is ScanState.Completed -> {
                    isDone = true
                    isSuccess = true
                    notificationManager.notify(
                        NOTIFICATION_ID,
                        NotificationCompat.Builder(appContext, CHANNEL_ID)
                            .setSmallIcon(android.R.drawable.ic_popup_sync)
                            .setContentTitle("Scan Complete")
                            .setContentText("Analyzed ${StorageFormatter.formatFileCount(state.totalFiles)} (${StorageFormatter.formatBytes(state.totalBytes)})")
                            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                            .setAutoCancel(true)
                            .build()
                    )
                }
                is ScanState.Error -> {
                    isDone = true
                    isSuccess = false
                }
                is ScanState.Cancelled -> {
                    isDone = true
                    isSuccess = false
                }
                is ScanState.Idle -> {
                    // Idle
                }
            }
        }

        if (isSuccess) Result.success() else Result.failure()
    }

    private fun createNotification(
        progressText: String,
        progressPercent: Int,
        isIndeterminate: Boolean
    ) = NotificationCompat.Builder(appContext, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_popup_sync)
        .setContentTitle("Quarry Storage Scan")
        .setContentText(progressText)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .setProgress(100, progressPercent, isIndeterminate)
        .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Storage Scan Progress",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live progress during storage analysis"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val WORK_NAME = "app.quarry.tanvir.info.SCAN_WORKER"
        const val CHANNEL_ID = "quarry_scan_channel"
        const val NOTIFICATION_ID = 1001

        fun enqueueScan(context: Context) {
            val request = OneTimeWorkRequestBuilder<ScanWorker>().build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
