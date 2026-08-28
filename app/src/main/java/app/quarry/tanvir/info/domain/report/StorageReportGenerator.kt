package app.quarry.tanvir.info.domain.report

import android.content.Context
import android.content.Intent
import app.quarry.tanvir.info.data.database.CategoryStat
import app.quarry.tanvir.info.data.database.FileEntity
import app.quarry.tanvir.info.data.database.ScanSnapshotEntity
import app.quarry.tanvir.info.domain.model.StorageCategory
import app.quarry.tanvir.info.domain.model.StorageFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object StorageReportGenerator {

    fun generateReportText(
        volumeName: String,
        totalBytes: Long,
        usedBytes: Long,
        freeBytes: Long,
        categoryStats: List<CategoryStat>,
        largestFiles: List<FileEntity>,
        snapshots: List<ScanSnapshotEntity> = emptyList()
    ): String {
        val dateFormat = SimpleDateFormat("MMMM dd, yyyy HH:mm:ss", Locale.US)
        val nowFormatted = dateFormat.format(Date())
        val usedPct = if (totalBytes > 0) (usedBytes.toDouble() / totalBytes.toDouble()) * 100.0 else 0.0

        val sb = StringBuilder()
        sb.append("=========================================\n")
        sb.append("         QUARRY STORAGE REPORT           \n")
        sb.append("       See where your storage goes       \n")
        sb.append("=========================================\n\n")

        sb.append("Generated: $nowFormatted\n")
        sb.append("Volume: $volumeName\n")
        sb.append("Total Storage: ${StorageFormatter.formatBytes(totalBytes)}\n")
        sb.append("Used Storage:  ${StorageFormatter.formatBytes(usedBytes)} (${String.format(Locale.US, "%.1f", usedPct)}%)\n")
        sb.append("Free Storage:  ${StorageFormatter.formatBytes(freeBytes)}\n\n")

        sb.append("-----------------------------------------\n")
        sb.append("CATEGORY BREAKDOWN\n")
        sb.append("-----------------------------------------\n")
        val statsMap = categoryStats.associate { it.category to it }
        StorageCategory.entries.forEach { category ->
            val stat = statsMap[category.name]
            val catBytes = stat?.totalBytes ?: 0L
            val catFiles = stat?.fileCount ?: 0L
            val catPct = if (usedBytes > 0) (catBytes.toDouble() / usedBytes.toDouble()) * 100.0 else 0.0
            sb.append(String.format(
                Locale.US,
                "%-12s: %10s  (%5.1f%%)  [%d files]\n",
                category.displayName,
                StorageFormatter.formatBytes(catBytes),
                catPct,
                catFiles
            ))
        }
        sb.append("\n")

        if (largestFiles.isNotEmpty()) {
            sb.append("-----------------------------------------\n")
            sb.append("TOP LARGEST FILES\n")
            sb.append("-----------------------------------------\n")
            largestFiles.take(10).forEachIndexed { index, file ->
                sb.append(String.format(
                    Locale.US,
                    "#%02d  %-10s  %s\n     %s\n",
                    index + 1,
                    StorageFormatter.formatBytes(file.size),
                    file.name,
                    file.path
                ))
            }
            sb.append("\n")
        }

        if (snapshots.size >= 2) {
            val latest = snapshots[0]
            val prev = snapshots[1]
            val delta = latest.usedBytes - prev.usedBytes
            sb.append("-----------------------------------------\n")
            sb.append("STORAGE GROWTH COMPARISON\n")
            sb.append("-----------------------------------------\n")
            sb.append("Change since previous scan: ${if (delta >= 0) "+" else "-"}${StorageFormatter.formatBytes(Math.abs(delta))}\n\n")
        }

        sb.append("=========================================\n")
        sb.append("Generated locally by Quarry App.\n")
        sb.append("Zero cloud telemetry. 100% Offline.\n")
        sb.append("=========================================\n")

        return sb.toString()
    }

    fun shareReport(context: Context, reportText: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Quarry Storage Analysis Report")
            putExtra(Intent.EXTRA_TEXT, reportText)
        }
        val chooser = Intent.createChooser(sendIntent, "Share Storage Report").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
