package app.quarry.tanvir.info.domain.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.util.LruCache
import android.util.Size
import app.quarry.tanvir.info.domain.model.StorageCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * High-performance, offline thumbnail loader and memory cache for local storage files.
 * Supports Images, Videos, APKs, PDF Documents, and Audio Album Art.
 */
object ThumbnailLoader {

    // Cache ~25MB of bitmap memory
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 8).coerceIn(16 * 1024, 48 * 1024)

    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    /**
     * Retrieves a cached bitmap or decodes a new thumbnail on Dispatchers.IO.
     */
    suspend fun loadThumbnail(
        context: Context,
        path: String,
        category: StorageCategory,
        targetWidth: Int = 128,
        targetHeight: Int = 128,
        lastModified: Long = 0L
    ): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "${path}_${lastModified}_${targetWidth}x$targetHeight"
        memoryCache.get(cacheKey)?.let { return@withContext it }

        val file = File(path)
        if (!file.exists() || !file.canRead() || file.isDirectory) {
            return@withContext null
        }

        val bitmap = try {
            when (category) {
                StorageCategory.IMAGES -> decodeImageThumbnail(file, targetWidth, targetHeight)
                StorageCategory.VIDEOS -> decodeVideoThumbnail(file, targetWidth, targetHeight)
                StorageCategory.APKS -> decodeApkIcon(context, file)
                StorageCategory.DOCUMENTS -> {
                    if (path.endsWith(".pdf", ignoreCase = true)) {
                        decodePdfThumbnail(file, targetWidth, targetHeight)
                    } else {
                        null
                    }
                }
                StorageCategory.AUDIO -> decodeAudioAlbumArt(file, targetWidth, targetHeight)
                else -> null
            }
        } catch (_: Throwable) {
            null
        }

        if (bitmap != null) {
            memoryCache.put(cacheKey, bitmap)
        }

        bitmap
    }

    private fun decodeImageThumbnail(file: File, width: Int, height: Int): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.media.ThumbnailUtils.createImageThumbnail(
                    file,
                    Size(width, height),
                    CancellationSignal()
                )
            } else {
                decodeSampledBitmapFromFile(file.absolutePath, width, height)
            }
        } catch (_: Throwable) {
            decodeSampledBitmapFromFile(file.absolutePath, width, height)
        }
    }

    private fun decodeVideoThumbnail(file: File, width: Int, height: Int): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.media.ThumbnailUtils.createVideoThumbnail(
                    file,
                    Size(width, height),
                    CancellationSignal()
                )
            } else {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(file.absolutePath)
                    retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } finally {
                    retriever.release()
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun decodeApkIcon(context: Context, file: File): Bitmap? {
        return try {
            val pm = context.packageManager
            val packageInfo = pm.getPackageArchiveInfo(file.absolutePath, 0) ?: return null
            val appInfo = packageInfo.applicationInfo ?: return null
            appInfo.sourceDir = file.absolutePath
            appInfo.publicSourceDir = file.absolutePath
            val drawable = appInfo.loadIcon(pm)
            drawableToBitmap(drawable)
        } catch (_: Throwable) {
            null
        }
    }

    private fun decodePdfThumbnail(file: File, targetWidth: Int, targetHeight: Int): Bitmap? {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null
        return try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            if (renderer.pageCount > 0) {
                page = renderer.openPage(0)
                val scale = (targetWidth.toFloat() / page.width.toFloat()).coerceAtMost(
                    targetHeight.toFloat() / page.height.toFloat()
                )
                val destWidth = (page.width * scale).toInt().coerceAtLeast(1)
                val destHeight = (page.height * scale).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(destWidth, destHeight, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        } finally {
            page?.close()
            renderer?.close()
            pfd?.close()
        }
    }

    private fun decodeAudioAlbumArt(file: File, width: Int, height: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val art = retriever.embeddedPicture ?: return null
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(art, 0, art.size, options)
            options.inSampleSize = calculateInSampleSize(options, width, height)
            options.inJustDecodeBounds = false
            BitmapFactory.decodeByteArray(art, 0, art.size, options)
        } catch (_: Throwable) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Throwable) {}
        }
    }

    private fun decodeSampledBitmapFromFile(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(path, options)
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.RGB_565
        return BitmapFactory.decodeFile(path, options)
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize.coerceAtLeast(1)
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap? {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
