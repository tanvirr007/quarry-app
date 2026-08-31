package app.quarry.tanvir.info.ui.explore

import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import app.quarry.tanvir.info.R
import app.quarry.tanvir.info.domain.model.StorageFormatter
import app.quarry.tanvir.info.domain.treemap.TreemapNode
import app.quarry.tanvir.info.domain.treemap.TreemapRect
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 5f

/**
 * Responsive, hardware-accelerated Treemap Canvas.
 * - Every individual file and folder is rendered with distinct, vibrant, harmonized colors.
 * - Fluid navigation animations provide visual continuity when drilling down into or popping out of folders.
 * - Tactile press feedback highlights tapped tiles with a luminous pulse.
 * - Smooth animated zoom reset on double-tap and seamless gesture panning/zooming.
 * - Hit-testing is performed in unscaled content coordinates so tiny tiles stay tappable while zoomed.
 */
@Composable
fun TreemapCanvas(
    nodes: List<TreemapNode>,
    onNodeClick: (TreemapNode) -> Unit,
    onSizeMeasured: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    currentPath: String = ""
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val coroutineScope = rememberCoroutineScope()

    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val borderColor = if (isDark) {
        Color.White.copy(alpha = 0.20f)
    } else {
        Color.Black.copy(alpha = 0.14f)
    }

    val customTypeface = remember(context) {
        try {
            ResourcesCompat.getFont(context, R.font.google_sans_rounded)
        } catch (_: Exception) {
            Typeface.DEFAULT
        }
    }

    val textPaint = remember(density, isDark, customTypeface) {
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            typeface = customTypeface?.let { Typeface.create(it, Typeface.BOLD) } ?: Typeface.DEFAULT_BOLD
            isAntiAlias = true
            isFakeBoldText = true
        }
    }

    val subtextPaint = remember(density, isDark, customTypeface) {
        android.graphics.Paint().apply {
            color = android.graphics.Color.argb(240, 255, 255, 255)
            typeface = customTypeface ?: Typeface.DEFAULT
            isAntiAlias = true
        }
    }

    // Directory Transition Animation
    val transitionAnim = remember { Animatable(1f) }
    var lastPath by remember { mutableStateOf(currentPath) }
    var initialTransitionScale by remember { mutableFloatStateOf(1f) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(surfaceColor.copy(alpha = 0.25f))
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val touchRadiusPx = density.run { 28.dp.toPx() }

        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        var pressedNode by remember { mutableStateOf<TreemapNode?>(null) }

        val scaleUpdated by rememberUpdatedState(scale)
        val offsetUpdated by rememberUpdatedState(offset)

        fun clampOffset(raw: Offset, scl: Float): Offset {
            if (scl <= 1f) return Offset.Zero
            val maxX = (widthPx * (scl - 1f)) / 2f
            val maxY = (heightPx * (scl - 1f)) / 2f
            return Offset(
                x = raw.x.coerceIn(-maxX, maxX),
                y = raw.y.coerceIn(-maxY, maxY)
            )
        }

        LaunchedEffect(widthPx, heightPx) {
            if (widthPx > 0f && heightPx > 0f) {
                onSizeMeasured(widthPx, heightPx)
            }
        }

        LaunchedEffect(widthPx, heightPx, scale) {
            offset = clampOffset(offset, scale)
        }

        // Trigger smooth drill-down or drill-up navigation animation on path change
        LaunchedEffect(currentPath) {
            if (lastPath != currentPath && currentPath.isNotEmpty()) {
                val isDrillDown = lastPath.isNotEmpty() && currentPath.startsWith(lastPath)
                val isDrillUp = lastPath.isNotEmpty() && lastPath.startsWith(currentPath)
                initialTransitionScale = when {
                    isDrillDown -> 0.91f
                    isDrillUp -> 1.09f
                    else -> 0.95f
                }
                lastPath = currentPath

                // Reset zoom when navigating directories
                if (scale > 1.01f || offset != Offset.Zero) {
                    scale = 1f
                    offset = Offset.Zero
                }

                transitionAnim.snapTo(0f)
                transitionAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                )
            } else if (transitionAnim.value < 1f) {
                transitionAnim.snapTo(1f)
            }
        }

        // Empty directory state with fade transition
        AnimatedVisibility(
            visible = nodes.isEmpty(),
            enter = fadeIn(animationSpec = tween(220)),
            exit = fadeOut(animationSpec = tween(150)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
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
        }

        if (nodes.isNotEmpty()) {
            // Outer box handles pinch-zoom + pan (transform). Inner box handles taps.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .pointerInput(widthPx, heightPx) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val oldScale = scaleUpdated
                            val curOffset = offsetUpdated
                            val newScale = (oldScale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                            if (newScale == oldScale && pan == Offset.Zero) return@detectTransformGestures

                            val center = Offset(widthPx / 2f, heightPx / 2f)

                            var newOffset = if (zoom != 1f && oldScale != 0f) {
                                val contentUnderCentroid = Offset(
                                    x = (centroid.x - center.x - curOffset.x) / oldScale + center.x,
                                    y = (centroid.y - center.y - curOffset.y) / oldScale + center.y
                                )
                                Offset(
                                    x = centroid.x - center.x - (contentUnderCentroid.x - center.x) * newScale,
                                    y = centroid.y - center.y - (contentUnderCentroid.y - center.y) * newScale
                                )
                            } else {
                                curOffset + pan
                            }

                            if (newScale <= 1f + 1e-3f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = newScale
                                newOffset = clampOffset(newOffset, newScale)
                                offset = newOffset
                            }
                        }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(nodes, widthPx, heightPx, touchRadiusPx) {
                            detectTapGestures(
                                onPress = { pressOffset ->
                                    val currentScale = scaleUpdated
                                    val currentOffset = offsetUpdated
                                    val center = Offset(widthPx / 2f, heightPx / 2f)
                                    val contentTap = if (currentScale <= 1f) {
                                        pressOffset
                                    } else {
                                        Offset(
                                            x = (pressOffset.x - center.x - currentOffset.x) / currentScale + center.x,
                                            y = (pressOffset.y - center.y - currentOffset.y) / currentScale + center.y
                                        )
                                    }
                                    val effectiveRadius = if (currentScale > 1f) touchRadiusPx / currentScale else touchRadiusPx
                                    val hitNode = findBestMatchingNode(
                                        nodes = nodes,
                                        x = contentTap.x,
                                        y = contentTap.y,
                                        touchRadiusPx = effectiveRadius
                                    )
                                    pressedNode = hitNode
                                    try {
                                        tryAwaitRelease()
                                    } finally {
                                        pressedNode = null
                                    }
                                },
                                onDoubleTap = {
                                    coroutineScope.launch {
                                        val startScale = scale
                                        val startOffset = offset
                                        val resetAnim = Animatable(0f)
                                        resetAnim.animateTo(
                                            targetValue = 1f,
                                            animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
                                        ) {
                                            scale = startScale + (1f - startScale) * value
                                            offset = Offset(
                                                startOffset.x * (1f - value),
                                                startOffset.y * (1f - value)
                                            )
                                        }
                                        scale = 1f
                                        offset = Offset.Zero
                                    }
                                },
                                onTap = { tapOffset ->
                                    val currentScale = scaleUpdated
                                    val currentOffset = offsetUpdated
                                    val center = Offset(widthPx / 2f, heightPx / 2f)
                                    val contentTap = if (currentScale <= 1f) {
                                        tapOffset
                                    } else {
                                        Offset(
                                            x = (tapOffset.x - center.x - currentOffset.x) / currentScale + center.x,
                                            y = (tapOffset.y - center.y - currentOffset.y) / currentScale + center.y
                                        )
                                    }
                                    val effectiveRadius = if (currentScale > 1f) touchRadiusPx / currentScale else touchRadiusPx
                                    val clickedNode = findBestMatchingNode(
                                        nodes = nodes,
                                        x = contentTap.x,
                                        y = contentTap.y,
                                        touchRadiusPx = effectiveRadius
                                    )
                                    if (clickedNode != null) {
                                        onNodeClick(clickedNode)
                                    }
                                }
                            )
                        }
                ) {
                    val animVal = transitionAnim.value
                    val currentTransitionScale = initialTransitionScale + (1.0f - initialTransitionScale) * animVal
                    val currentTransitionAlpha = (animVal * 1.35f).coerceAtMost(1f)

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale * currentTransitionScale,
                                scaleY = scale * currentTransitionScale,
                                translationX = offset.x,
                                translationY = offset.y,
                                alpha = currentTransitionAlpha
                            )
                    ) {
                        val strokeWidthPx = (1.0.dp.toPx() / scale).coerceAtLeast(0.5f)
                        val cornerRadiusPx = (6.dp.toPx() / scale.coerceAtLeast(1f)).coerceAtLeast(2f / scale)
                        val cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)

                        val baseTitleSize = density.run { 11.5.dp.toPx() }
                        val baseSubtextSize = density.run { 9.5.dp.toPx() }

                        textPaint.textSize = baseTitleSize / scale
                        textPaint.setShadowLayer(
                            (3f / scale).coerceAtLeast(0.5f),
                            0f,
                            (1.5f / scale).coerceAtLeast(0.5f),
                            android.graphics.Color.argb(220, 0, 0, 0)
                        )

                        subtextPaint.textSize = baseSubtextSize / scale
                        subtextPaint.setShadowLayer(
                            (2.5f / scale).coerceAtLeast(0.5f),
                            0f,
                            (1f / scale).coerceAtLeast(0.5f),
                            android.graphics.Color.argb(200, 0, 0, 0)
                        )

                        val minVisualWForTitle = density.run { 26.dp.toPx() }
                        val minVisualHForTitle = density.run { 16.dp.toPx() }
                        val minVisualWForSubtext = density.run { 30.dp.toPx() }
                        val minVisualHForSubtext = density.run { 28.dp.toPx() }

                        val paddingX = density.run { 5.dp.toPx() } / scale
                        val titleTopOffset = density.run { 12.dp.toPx() } / scale
                        val subtextTopOffset = density.run { 11.dp.toPx() } / scale

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

                                val halfStroke = strokeWidthPx / 2f
                                val drawRectTopLeft = Offset(rect.left + halfStroke, rect.top + halfStroke)
                                val drawRectSize = Size(
                                    (w - strokeWidthPx).coerceAtLeast(0.5f),
                                    (h - strokeWidthPx).coerceAtLeast(0.5f)
                                )

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

                                drawRoundRect(
                                    color = borderColor,
                                    topLeft = drawRectTopLeft,
                                    size = drawRectSize,
                                    cornerRadius = cornerRadius,
                                    style = Stroke(width = strokeWidthPx)
                                )

                                val visualW = w * scale
                                val visualH = h * scale

                                if (visualW >= minVisualWForTitle && visualH >= minVisualHForTitle) {
                                    val maxTextWidth = w - (2f * paddingX)
                                    if (maxTextWidth > 0f) {
                                        val fullName = node.name
                                        val nameWidth = textPaint.measureText(fullName)
                                        val displayName = if (nameWidth <= maxTextWidth) {
                                            fullName
                                        } else {
                                            val ellipsis = "…"
                                            val ellipsisWidth = textPaint.measureText(ellipsis)
                                            val availableForChars = maxTextWidth - ellipsisWidth
                                            if (availableForChars > 0f) {
                                                val charsCount = textPaint.breakText(fullName, true, availableForChars, null)
                                                if (charsCount > 0) {
                                                    fullName.take(charsCount) + ellipsis
                                                } else {
                                                    ""
                                                }
                                            } else {
                                                ""
                                            }
                                        }

                                        if (displayName.isNotEmpty()) {
                                            val xPos = rect.left + paddingX
                                            val yPos = rect.top + titleTopOffset

                                            drawContext.canvas.nativeCanvas.drawText(displayName, xPos, yPos, textPaint)

                                            if (visualW >= minVisualWForSubtext && visualH >= minVisualHForSubtext) {
                                                val sizeText = StorageFormatter.formatBytes(node.size)
                                                val sizeWidth = subtextPaint.measureText(sizeText)
                                                val displaySize = if (sizeWidth <= maxTextWidth) {
                                                    sizeText
                                                } else {
                                                    val ellipsis = "…"
                                                    val ellipsisWidth = subtextPaint.measureText(ellipsis)
                                                    val availableForChars = maxTextWidth - ellipsisWidth
                                                    if (availableForChars > 0f) {
                                                        val charsCount = subtextPaint.breakText(sizeText, true, availableForChars, null)
                                                        if (charsCount >= 3) {
                                                            sizeText.take(charsCount) + ellipsis
                                                        } else {
                                                            ""
                                                        }
                                                    } else {
                                                        ""
                                                    }
                                                }

                                                if (displaySize.isNotEmpty()) {
                                                    drawContext.canvas.nativeCanvas.drawText(
                                                        displaySize,
                                                        xPos,
                                                        yPos + subtextTopOffset,
                                                        subtextPaint
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Render glowing tactile feedback on actively pressed / tapped tile
                                if (node == pressedNode) {
                                    drawRoundRect(
                                        color = Color.White.copy(alpha = 0.28f),
                                        topLeft = drawRectTopLeft,
                                        size = drawRectSize,
                                        cornerRadius = cornerRadius
                                    )
                                    drawRoundRect(
                                        color = Color.White.copy(alpha = 0.60f),
                                        topLeft = drawRectTopLeft,
                                        size = drawRectSize,
                                        cornerRadius = cornerRadius,
                                        style = Stroke(width = strokeWidthPx * 1.5f)
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

private fun findBestMatchingNode(
    nodes: List<TreemapNode>,
    x: Float,
    y: Float,
    touchRadiusPx: Float
): TreemapNode? {
    if (nodes.isEmpty()) return null

    val directHits = nodes.filter { node ->
        x >= node.rect.left && x <= node.rect.right &&
                y >= node.rect.top && y <= node.rect.bottom
    }

    if (directHits.isNotEmpty()) {
        val smallestDirect = directHits.minByOrNull { it.rect.width * it.rect.height }

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
