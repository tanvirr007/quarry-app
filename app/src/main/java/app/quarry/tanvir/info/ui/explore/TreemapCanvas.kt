package app.quarry.tanvir.info.ui.explore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.quarry.tanvir.info.domain.model.StorageFormatter
import app.quarry.tanvir.info.domain.treemap.TreemapNode
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Responsive, hardware-accelerated Treemap Canvas with fluid touch-and-move
 * (single-finger drag/pan, multi-touch pinch zoom, double-tap zoom/reset, and tap selection).
 * Every individual file is rendered with a distinct, vibrant, harmonized color.
 */
@Composable
fun TreemapCanvas(
    nodes: List<TreemapNode>,
    onNodeClick: (TreemapNode) -> Unit,
    onSizeMeasured: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()

    val scaleAnim = remember { Animatable(1f) }
    val offsetAnim = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

    // Reset zoom and pan whenever the node list (current directory) changes
    LaunchedEffect(nodes) {
        if (scaleAnim.value != 1f || offsetAnim.value != Offset.Zero) {
            launch { scaleAnim.snapTo(1f) }
            launch { offsetAnim.snapTo(Offset.Zero) }
        }
    }

    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val borderColor = if (isDark) {
        Color.White.copy(alpha = 0.18f)
    } else {
        Color.Black.copy(alpha = 0.12f)
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
            color = android.graphics.Color.argb(235, 255, 255, 255)
            textSize = density.run { 10.dp.toPx() }
            isAntiAlias = true
            setShadowLayer(3f, 0f, 1f, android.graphics.Color.argb(200, 0, 0, 0))
        }
    }

    val dirBadgePaint = remember(density) {
        android.graphics.Paint().apply {
            color = android.graphics.Color.argb(240, 255, 255, 255)
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
                    var lastTapTime = 0L
                    var lastTapPos = Offset.Zero

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downPos = down.position
                        var isDragOrZoom = false
                        var accumulatedPan = Offset.Zero

                        do {
                            val event = awaitPointerEvent()
                            val canceled = event.changes.any { it.isConsumed }
                            if (canceled) break

                            val pointerCount = event.changes.count { it.pressed }
                            if (pointerCount == 0) break

                            val pan = event.calculatePan()
                            val zoom = event.calculateZoom()

                            accumulatedPan += pan

                            // Touch-and-move: handle 1-finger drag and 2-finger zoom/pan
                            if (accumulatedPan.getDistance() > 8f || abs(zoom - 1f) > 0.02f) {
                                isDragOrZoom = true
                            }

                            if (isDragOrZoom) {
                                coroutineScope.launch {
                                    val currentScale = scaleAnim.value
                                    val newScale = (currentScale * zoom).coerceIn(1f, 8f)
                                    scaleAnim.snapTo(newScale)

                                    val maxOffsetX = (widthPx * (newScale - 1f) / 2f).coerceAtLeast(0f)
                                    val maxOffsetY = (heightPx * (newScale - 1f) / 2f).coerceAtLeast(0f)

                                    val newOffset = offsetAnim.value + pan
                                    val clampedX = newOffset.x.coerceIn(-maxOffsetX, maxOffsetX)
                                    val clampedY = newOffset.y.coerceIn(-maxOffsetY, maxOffsetY)

                                    if (newScale <= 1.01f) {
                                        offsetAnim.snapTo(Offset.Zero)
                                    } else {
                                        offsetAnim.snapTo(Offset(clampedX, clampedY))
                                    }
                                }

                                event.changes.forEach {
                                    if (it.positionChanged()) it.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        // If the user tapped without dragging
                        if (!isDragOrZoom) {
                            val currentTime = System.currentTimeMillis()
                            val isDoubleTap = (currentTime - lastTapTime < 320L) &&
                                    ((downPos - lastTapPos).getDistance() < 60f)

                            if (isDoubleTap) {
                                lastTapTime = 0L
                                coroutineScope.launch {
                                    if (scaleAnim.value > 1.2f) {
                                        // Reset zoom smoothly
                                        launch {
                                            scaleAnim.animateTo(
                                                1f,
                                                animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f)
                                            )
                                        }
                                        launch {
                                            offsetAnim.animateTo(
                                                Offset.Zero,
                                                animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f)
                                            )
                                        }
                                    } else {
                                        // Zoom in to 2.8x centered at the tapped location
                                        val targetScale = 2.8f
                                        val centerX = widthPx / 2f
                                        val centerY = heightPx / 2f
                                        val targetOffset = Offset(
                                            x = (centerX - downPos.x) * (targetScale - 1f),
                                            y = (centerY - downPos.y) * (targetScale - 1f)
                                        )
                                        launch {
                                            scaleAnim.animateTo(
                                                targetScale,
                                                animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f)
                                            )
                                        }
                                        launch {
                                            offsetAnim.animateTo(
                                                targetOffset,
                                                animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f)
                                            )
                                        }
                                    }
                                }
                            } else {
                                lastTapTime = currentTime
                                lastTapPos = downPos

                                // Single tap detection
                                val currentScale = scaleAnim.value
                                val currentOffset = offsetAnim.value
                                val centerX = widthPx / 2f
                                val centerY = heightPx / 2f

                                val localX = centerX + (downPos.x - centerX - currentOffset.x) / currentScale
                                val localY = centerY + (downPos.y - centerY - currentOffset.y) / currentScale

                                val clickedNode = nodes.firstOrNull { node ->
                                    localX >= node.rect.left && localX <= node.rect.right &&
                                            localY >= node.rect.top && localY <= node.rect.bottom
                                }
                                if (clickedNode != null) {
                                    onNodeClick(clickedNode)
                                }
                            }
                        }
                    }
                }
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scaleAnim.value
                        scaleY = scaleAnim.value
                        translationX = offsetAnim.value.x
                        translationY = offsetAnim.value.y
                    }
            ) {
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
                        if (w > 48.dp.toPx() && h > 30.dp.toPx()) {
                            drawContext.canvas.nativeCanvas.apply {
                                val paddingPx = density.run { 6.dp.toPx() }
                                val maxChars = (w / density.run { 7.5.dp.toPx() }).toInt().coerceAtLeast(4)
                                val displayName = if (node.name.length > maxChars) {
                                    node.name.take(maxChars - 2) + "…"
                                } else {
                                    node.name
                                }

                                val sizeText = StorageFormatter.formatBytes(node.size)
                                val xPos = rect.left + paddingPx
                                var yPos = rect.top + density.run { 15.dp.toPx() }

                                if (node.isDirectory && h > 42.dp.toPx()) {
                                    drawText("DIR", xPos, yPos - density.run { 2.dp.toPx() }, dirBadgePaint)
                                    yPos += density.run { 12.dp.toPx() }
                                }

                                drawText(displayName, xPos, yPos, textPaint)

                                if (h > 46.dp.toPx()) {
                                    drawText(
                                        sizeText,
                                        xPos,
                                        yPos + density.run { 13.dp.toPx() },
                                        subtextPaint
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Reset Zoom Button when zoomed in
        val isZoomed by remember { derivedStateOf { scaleAnim.value > 1.15f } }
        AnimatedVisibility(
            visible = isZoomed,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(150)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
        ) {
            FloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        launch {
                            scaleAnim.animateTo(
                                1f,
                                animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f)
                            )
                        }
                        launch {
                            offsetAnim.animateTo(
                                Offset.Zero,
                                animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f)
                            )
                        }
                    }
                },
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ZoomOutMap,
                    contentDescription = "Reset Zoom",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
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
