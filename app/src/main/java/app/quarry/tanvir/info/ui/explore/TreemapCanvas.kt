package app.quarry.tanvir.info.ui.explore

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.quarry.tanvir.info.domain.model.StorageFormatter
import app.quarry.tanvir.info.domain.treemap.TreemapNode
import app.quarry.tanvir.info.domain.treemap.TreemapRect
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Responsive, hardware-accelerated Treemap Canvas.
 * Every individual file and folder is rendered with distinct, vibrant, harmonized colors.
 * Direct tap interactions allow seamless navigation into folders or viewing file details.
 */
@Composable
fun TreemapCanvas(
    nodes: List<TreemapNode>,
    onNodeClick: (TreemapNode) -> Unit,
    onSizeMeasured: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val isDark = isSystemInDarkTheme()

    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val borderColor = if (isDark) {
        Color.White.copy(alpha = 0.20f)
    } else {
        Color.Black.copy(alpha = 0.14f)
    }

    val textPaint = remember(density, isDark) {
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = density.run { 12.dp.toPx() }
            isAntiAlias = true
            isFakeBoldText = true
            setShadowLayer(4f, 0f, 2f, android.graphics.Color.argb(220, 0, 0, 0))
        }
    }

    val subtextPaint = remember(density, isDark) {
        android.graphics.Paint().apply {
            color = android.graphics.Color.argb(240, 255, 255, 255)
            textSize = density.run { 10.dp.toPx() }
            isAntiAlias = true
            setShadowLayer(3f, 0f, 1f, android.graphics.Color.argb(200, 0, 0, 0))
        }
    }

    val dirBadgePaint = remember(density) {
        android.graphics.Paint().apply {
            color = android.graphics.Color.argb(245, 255, 255, 255)
            textSize = density.run { 9.dp.toPx() }
            isAntiAlias = true
            isFakeBoldText = true
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
                text = "No files found in this directory.\nScan storage or select another folder.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(24.dp)
            )
        }
        return
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(surfaceColor.copy(alpha = 0.25f))
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val touchRadiusPx = density.run { 28.dp.toPx() }

        LaunchedEffect(widthPx, heightPx) {
            if (widthPx > 0f && heightPx > 0f) {
                onSizeMeasured(widthPx, heightPx)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(nodes, widthPx, heightPx) {
                    detectTapGestures(
                        onTap = { tapOffset ->
                            val clickedNode = findBestMatchingNode(
                                nodes = nodes,
                                x = tapOffset.x,
                                y = tapOffset.y,
                                touchRadiusPx = touchRadiusPx
                            )
                            if (clickedNode != null) {
                                onNodeClick(clickedNode)
                            }
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidthPx = 1.2.dp.toPx()
                val cornerRadiusPx = 6.dp.toPx()
                val cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)

                nodes.forEachIndexed { index, node ->
                    val rect = node.rect
                    val w = rect.width
                    val h = rect.height

                    if (w > 2f && h > 2f) {
                        val (colorTop, colorBottom) = getTreemapTileColors(
                            node = node,
                            index = index,
                            isDark = isDark
                        )

                        val drawRectTopLeft = Offset(rect.left + 1f, rect.top + 1f)
                        val drawRectSize = Size(
                            (w - 2f).coerceAtLeast(1f),
                            (h - 2f).coerceAtLeast(1f)
                        )

                        // Draw Gradient Fill for rich depth
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(colorTop, colorBottom),
                                startY = rect.top,
                                endY = rect.bottom
                            ),
                            topLeft = drawRectTopLeft,
                            size = drawRectSize,
                            cornerRadius = cornerRadius
                        )

                        // Draw Crisp Outline Border
                        drawRoundRect(
                            color = borderColor,
                            topLeft = drawRectTopLeft,
                            size = drawRectSize,
                            cornerRadius = cornerRadius,
                            style = Stroke(width = strokeWidthPx)
                        )

                        // Draw Text and Badges if rectangle is large enough
                        if (w > 28.dp.toPx() && h > 18.dp.toPx()) {
                            drawContext.canvas.nativeCanvas.apply {
                                val paddingPx = density.run { 6.dp.toPx() }
                                val maxChars = (w / density.run { 7.dp.toPx() }).toInt().coerceAtLeast(3)
                                val displayName = if (node.name.length > maxChars) {
                                    node.name.take(maxChars - 1) + "…"
                                } else {
                                    node.name
                                }

                                val sizeText = StorageFormatter.formatBytes(node.size)
                                val xPos = rect.left + paddingPx
                                var yPos = rect.top + density.run { 13.dp.toPx() }

                                if (node.isDirectory && h > 36.dp.toPx()) {
                                    drawText("DIR", xPos, yPos - density.run { 2.dp.toPx() }, dirBadgePaint)
                                    yPos += density.run { 11.dp.toPx() }
                                }

                                drawText(displayName, xPos, yPos, textPaint)

                                if (h > 38.dp.toPx()) {
                                    drawText(
                                        sizeText,
                                        xPos,
                                        yPos + density.run { 12.dp.toPx() },
                                        subtextPaint
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Finds the node closest to or directly containing (x, y), prioritizing smaller
 * or narrow nodes so they are effortlessly tapped without requiring pinpoint accuracy.
 */
private fun findBestMatchingNode(
    nodes: List<TreemapNode>,
    x: Float,
    y: Float,
    touchRadiusPx: Float
): TreemapNode? {
    if (nodes.isEmpty()) return null

    // 1. Check all nodes containing the tap point directly
    val directHits = nodes.filter { node ->
        x >= node.rect.left && x <= node.rect.right &&
                y >= node.rect.top && y <= node.rect.bottom
    }

    if (directHits.isNotEmpty()) {
        val smallestDirect = directHits.minByOrNull { it.rect.width * it.rect.height }

        // If tap is inside a large tile but close to a smaller neighbor tile, prioritize the smaller neighbor
        val nearbySmallNode = nodes
            .filter { node ->
                val dist = distanceToRect(x, y, node.rect)
                dist <= touchRadiusPx && (node.rect.width * node.rect.height < (smallestDirect?.let { it.rect.width * it.rect.height * 0.3f } ?: Float.MAX_VALUE))
            }
            .minByOrNull { distanceToRect(x, y, it.rect) }

        if (nearbySmallNode != null && distanceToRect(x, y, nearbySmallNode.rect) <= touchRadiusPx * 0.75f) {
            return nearbySmallNode
        }

        return smallestDirect
    }

    // 2. Proximity fallback: find nearest node within touch tolerance
    val candidate = nodes.minByOrNull { distanceToRect(x, y, it.rect) }
    if (candidate != null && distanceToRect(x, y, candidate.rect) <= touchRadiusPx * 1.5f) {
        return candidate
    }

    return null
}

private fun distanceToRect(x: Float, y: Float, rect: TreemapRect): Float {
    val dx = when {
        x < rect.left -> rect.left - x
        x > rect.right -> x - rect.right
        else -> 0f
    }
    val dy = when {
        y < rect.top -> rect.top - y
        y > rect.bottom -> y - rect.bottom
        else -> 0f
    }
    return sqrt(dx * dx + dy * dy)
}

/**
 * Calculates a unique, vibrant, harmonized color pair (top and bottom gradient)
 * for each individual file and directory so every file in the treemap has its own distinct color.
 */
private fun getTreemapTileColors(
    node: TreemapNode,
    index: Int,
    isDark: Boolean
): Pair<Color, Color> {
    val pathHash = abs(node.path.hashCode())
    val hue = ((index * 137.508f) + (pathHash % 71)) % 360f

    val saturation = if (node.isDirectory) 0.65f else 0.78f
    val lightness = if (isDark) {
        if (node.isDirectory) 0.44f else 0.50f
    } else {
        if (node.isDirectory) 0.40f else 0.46f
    }

    val topLightness = (lightness + 0.06f).coerceAtMost(0.85f)
    val bottomLightness = (lightness - 0.06f).coerceAtLeast(0.18f)

    val topColor = hslToColor(hue, saturation, topLightness)
    val bottomColor = hslToColor(hue, saturation, bottomLightness)

    return Pair(topColor, bottomColor)
}

/**
 * Converts HSL color components to an Android Compose Color instance.
 */
private fun hslToColor(
    hue: Float,
    saturation: Float,
    lightness: Float,
    alpha: Float = 1f
): Color {
    val h = ((hue % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val l = lightness.coerceIn(0f, 1f)

    val c = (1f - abs(2f * l - 1f)) * s
    val x = c * (1f - abs(((h / 60f) % 2f) - 1f))
    val m = l - (c / 2f)

    val (rPrime, gPrime, bPrime) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }

    return Color(
        red = (rPrime + m).coerceIn(0f, 1f),
        green = (gPrime + m).coerceIn(0f, 1f),
        blue = (bPrime + m).coerceIn(0f, 1f),
        alpha = alpha
    )
}

/**
 * Calculates a unique, vibrant, harmonized color pair (top and bottom gradient)
 * for each individual file and directory so every file in the treemap has its own distinct color.
 */
private fun getTreemapTileColors(
    node: TreemapNode,
    index: Int,
    isDark: Boolean
): Pair<Color, Color> {
    val pathHash = abs(node.path.hashCode())
    val hue = ((index * 137.508f) + (pathHash % 71)) % 360f

    val saturation = if (node.isDirectory) 0.65f else 0.78f
    val lightness = if (isDark) {
        if (node.isDirectory) 0.44f else 0.50f
    } else {
        if (node.isDirectory) 0.40f else 0.46f
    }

    val topLightness = (lightness + 0.06f).coerceAtMost(0.85f)
    val bottomLightness = (lightness - 0.06f).coerceAtLeast(0.18f)

    val topColor = hslToColor(hue, saturation, topLightness)
    val bottomColor = hslToColor(hue, saturation, bottomLightness)

    return Pair(topColor, bottomColor)
}

/**
 * Converts HSL color components to an Android Compose Color instance.
 */
private fun hslToColor(
    hue: Float,
    saturation: Float,
    lightness: Float,
    alpha: Float = 1f
): Color {
    val h = ((hue % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val l = lightness.coerceIn(0f, 1f)

    val c = (1f - abs(2f * l - 1f)) * s
    val x = c * (1f - abs(((h / 60f) % 2f) - 1f))
    val m = l - (c / 2f)

    val (rPrime, gPrime, bPrime) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }

    return Color(
        red = (rPrime + m).coerceIn(0f, 1f),
        green = (gPrime + m).coerceIn(0f, 1f),
        blue = (bPrime + m).coerceIn(0f, 1f),
        alpha = alpha
    )
}
