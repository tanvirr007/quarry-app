package app.quarry.tanvir.info.ui.explore

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.quarry.tanvir.info.domain.model.StorageFormatter
import app.quarry.tanvir.info.domain.treemap.TreemapNode
import app.quarry.tanvir.info.ui.components.CategoryVisuals

@Composable
fun TreemapCanvas(
    nodes: List<TreemapNode>,
    onNodeClick: (TreemapNode) -> Unit,
    onSizeMeasured: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.8f, 5f)
        offset += offsetChange
    }

    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val outlineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val density = LocalDensity.current

    val textPaint = remember(density) {
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = density.run { 12.dp.toPx() }
            isAntiAlias = true
            isFakeBoldText = true
            setShadowLayer(3f, 0f, 1f, android.graphics.Color.argb(180, 0, 0, 0))
        }
    }

    val subtextPaint = remember(density) {
        android.graphics.Paint().apply {
            color = android.graphics.Color.argb(220, 255, 255, 255)
            textSize = density.run { 10.dp.toPx() }
            isAntiAlias = true
            setShadowLayer(2f, 0f, 1f, android.graphics.Color.argb(180, 0, 0, 0))
        }
    }

    if (nodes.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(surfaceColor.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No files found in this directory.\nRun a scan from Home to populate.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(24.dp)
            )
        }
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(surfaceColor.copy(alpha = 0.3f))
            .onSizeChanged { size ->
                onSizeMeasured(size.width.toFloat(), size.height.toFloat())
            }
            .transformable(state = transformState)
            .pointerInput(nodes) {
                detectTapGestures { tapOffset ->
                    val clickedNode = nodes.firstOrNull { node ->
                        tapOffset.x >= node.rect.left && tapOffset.x <= node.rect.right &&
                                tapOffset.y >= node.rect.top && tapOffset.y <= node.rect.bottom
                    }
                    if (clickedNode != null) {
                        onNodeClick(clickedNode)
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidthPx = 1.5.dp.toPx()
            val cornerRadiusPx = 6.dp.toPx()

            nodes.forEach { node ->
                val rect = node.rect
                val width = rect.width
                val height = rect.height

                if (width > 2 && height > 2) {
                    val baseColor = if (node.isDirectory) {
                        CategoryVisuals.getColor(node.category).copy(alpha = 0.85f)
                    } else {
                        CategoryVisuals.getColor(node.category)
                    }

                    // Fill Rectangle
                    drawRoundRect(
                        color = baseColor,
                        topLeft = Offset(rect.left + 1f, rect.top + 1f),
                        size = Size((width - 2f).coerceAtLeast(1f), (height - 2f).coerceAtLeast(1f)),
                        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                    )

                    // Stroke Outline
                    drawRoundRect(
                        color = outlineColor,
                        topLeft = Offset(rect.left + 1f, rect.top + 1f),
                        size = Size((width - 2f).coerceAtLeast(1f), (height - 2f).coerceAtLeast(1f)),
                        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                        style = Stroke(width = strokeWidthPx)
                    )

                    // Draw Text Labels if rectangle is large enough
                    if (width > 60.dp.toPx() && height > 36.dp.toPx()) {
                        drawContext.canvas.nativeCanvas.apply {
                            val maxChars = (width / density.run { 7.dp.toPx() }).toInt().coerceAtLeast(4)
                            val displayName = if (node.name.length > maxChars) {
                                node.name.take(maxChars - 2) + "…"
                            } else {
                                node.name
                            }

                            val sizeText = StorageFormatter.formatBytes(node.size)
                            val xPos = rect.left + density.run { 6.dp.toPx() }
                            val yPos = rect.top + density.run { 16.dp.toPx() }

                            drawText(displayName, xPos, yPos, textPaint)

                            if (height > 52.dp.toPx()) {
                                drawText(sizeText, xPos, yPos + density.run { 14.dp.toPx() }, subtextPaint)
                            }
                        }
                    }
                }
            }
        }
    }
}
