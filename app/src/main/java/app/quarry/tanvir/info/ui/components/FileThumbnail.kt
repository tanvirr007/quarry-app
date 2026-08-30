package app.quarry.tanvir.info.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.quarry.tanvir.info.domain.media.ThumbnailLoader
import app.quarry.tanvir.info.domain.model.StorageCategory

/**
 * Reusable file thumbnail composable.
 * Renders high-quality, memory-cached image/video/APK/PDF/audio previews with smooth fallback to category icons.
 */
@Composable
fun FileThumbnail(
    path: String,
    category: StorageCategory,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    shape: Shape = RoundedCornerShape(10.dp),
    lastModified: Long = 0L,
    isDirectory: Boolean = false,
    contentDescription: String? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val targetPx = remember(size, density) {
        with(density) { size.roundToPx().coerceAtLeast(64) }
    }

    var thumbnailBitmap by remember(path, lastModified) { mutableStateOf<Bitmap?>(null) }

    val isMediaOrPreviewable = !isDirectory && (
        category == StorageCategory.IMAGES ||
        category == StorageCategory.VIDEOS ||
        category == StorageCategory.APKS ||
        (category == StorageCategory.DOCUMENTS && path.endsWith(".pdf", ignoreCase = true)) ||
        category == StorageCategory.AUDIO
    )

    LaunchedEffect(path, lastModified, isMediaOrPreviewable) {
        if (isMediaOrPreviewable) {
            thumbnailBitmap = ThumbnailLoader.loadThumbnail(
                context = context,
                path = path,
                category = category,
                targetWidth = targetPx,
                targetHeight = targetPx,
                lastModified = lastModified
            )
        } else {
            thumbnailBitmap = null
        }
    }

    val categoryColor = if (isDirectory) MaterialTheme.colorScheme.primary else category.getColor()
    val categoryIcon = if (isDirectory) Icons.Rounded.Folder else category.getIcon()

    Box(
        modifier = modifier
            .size(size)
            .clip(shape),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(
            targetState = thumbnailBitmap,
            label = "FileThumbnailCrossfade"
        ) { bitmap ->
            if (bitmap != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = contentDescription,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Video indicator badge overlay
                    if (category == StorageCategory.VIDEOS) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(2.dp)
                                .size((size * 0.38f).coerceIn(14.dp, 20.dp))
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.65f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Video",
                                tint = Color.White,
                                modifier = Modifier.size((size * 0.28f).coerceIn(10.dp, 16.dp))
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(categoryColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = categoryIcon,
                        contentDescription = contentDescription,
                        tint = categoryColor,
                        modifier = Modifier.size((size * 0.55f).coerceAtLeast(16.dp))
                    )
                }
            }
        }
    }
}
