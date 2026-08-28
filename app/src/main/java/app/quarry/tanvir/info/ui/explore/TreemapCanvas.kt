package app.quarry.tanvir.info.ui.explore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.quarry.tanvir.info.domain.model.StorageFormatter
import app.quarry.tanvir.info.domain.treemap.TreemapNode
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Responsive, hardware-accelerated Treemap Canvas with Google Maps-style interactive navigation
 * (fluid single-finger pan, focal-point pinch-to-zoom, double-tap zoom/reset, and floating zoom controls).
 * Every individual file and folder is rendered with distinct, vibrant, harmonized colors.
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

    // Reset zoom and pan whenever the directory nodes change
    LaunchedEffect(nodes) {
        if (scaleAnim.value != 1f || offsetAnim.value != Offset.Zero) {
            launch { scaleAnim.snapTo(1f) }
            launch { offsetAnim.snapTo(Offset.Zero) }
        }
    }

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
                        val downTime = System.currentTimeMillis()
                        val downPos = down.position
                        var isTransforming = false
                        var totalPan = Offset.Zero
                        val touchSlop = viewConfiguration.touchSlop

                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.any { it.isConsumed }) break

                            val pressedCount = event.changes.count { it.pressed }
                            if (pressedCount == 0) break

                            val pan = event.calculatePan()
                            val zoom = event.calculateZoom()
                            val centroid = event.calculateCentroid(useCurrent = false)

                            totalPan += pan

                            // Determine if gesture is a pan or pinch-to-zoom
                            if (!isTransforming) {
                                if (pressedCount > 1 || totalPan.getDistance() > touchSlop || abs(zoom - 1f) > 0.015f) {
                                    isTransforming = true
                                }
                            }

                            if (isTransforming) {
                                coroutineScope.launch {
                                    val s0 = scaleAnim.value
                                    val t0 = offsetAnim.value
                                    val s1 = (s0 * zoom).coerceIn(1f, 10f)
                                    val r = s1 / s0
                                    val center = Offset(widthPx / 2f, heightPx / 2f)

                                    // Focal-point pinch zoom & pan transformation math
                                    val t1 = pan + (centroid - center) * (1f - r) + t0 * r
                                    val maxOffsetX = (widthPx * (s1 - 1f) / 2f).coerceAtLeast(0f)
                                    val maxOffsetY = (heightPx * (s1 - 1f) / 2f).coerceAtLeast(0f)

                                    val clampedOffset = if (s1 <= 1.001f) {
                                        Offset.Zero
                                    } else {
                                        Offset(
                                            t1.x.coerceIn(-maxOffsetX, maxOffsetX),
                                            t1.y.coerceIn(-maxOffsetY, maxOffsetY)
                                        )
                                    }

                                    scaleAnim.snapTo(s1)
                                    offsetAnim.snapTo(clampedOffset)
                                }

                                event.changes.forEach {
                                    if (it.positionChanged()) it.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        // Tap handling when no dragging/zooming occurred
                        if (!isTransforming) {
                            val currentTime = System.currentTimeMillis()
                            val elapsed = currentTime - downTime
                            val isDoubleTap = (currentTime - lastTapTime < 320L) &&
                                    ((downPos - lastTapPos).getDistance() < touchSlop * 2.5f)

                            if (isDoubleTap) {
                                lastTapTime = 0L
                                coroutineScope.launch {
                                    if (scaleAnim.value > 1.2f) {
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
                                        val targetScale = 2.8f
                                        val center = Offset(widthPx / 2f, heightPx / 2f)
                                        val maxOffsetX = (widthPx * (targetScale - 1f) / 2f).coerceAtLeast(0f)
                                        val maxOffsetY = (heightPx * (targetScale - 1f) / 2f).coerceAtLeast(0f)
                                        val targetOffset = Offset(
                                            x = ((center.x - downPos.x) * (targetScale - 1f)).coerceIn(-maxOffsetX, maxOffsetX),
                                            y = ((center.y - downPos.y) * (targetScale - 1f)).coerceIn(-maxOffsetY, maxOffsetY)
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
                            } else if (elapsed < 350L) {
                                lastTapTime = currentTime
                                lastTapPos = downPos

                                // Convert screen touch coordinates back to local untransformed canvas coordinates
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
                        if (w > 36.dp.toPx() && h > 22.dp.toPx()) {
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
                                var yPos = rect.top + density.run { 14.dp.toPx() }

                                if (node.isDirectory && h > 38.dp.toPx()) {
                                    drawText("DIR", xPos, yPos - density.run { 2.dp.toPx() }, dirBadgePaint)
                                    yPos += density.run { 11.dp.toPx() }
                                }

                                drawText(displayName, xPos, yPos, textPaint)

                                if (h > 42.dp.toPx()) {
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

        // Google Maps-style Interactive Floating Map Controls (Zoom In, Zoom Out, Reset, Zoom Indicator)
        val currentScale = scaleAnim.value
        val isZoomed by remember { derivedStateOf { currentScale > 1.05f } }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Reset / Fit to Screen Button (visible when zoomed)
            AnimatedVisibility(
                visible = isZoomed,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                Surface(
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
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shadowElevation = 4.dp,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.CenterFocusStrong,
                            contentDescription = "Fit to Screen",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Compact Zoom In / Out Pill Control
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.shadow(6.dp, RoundedCornerShape(20.dp))
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp)
                ) {
                    // Zoom In Button (+)
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                val targetScale = (scaleAnim.value * 1.5f).coerceIn(1f, 10f)
                                val maxOffsetX = (widthPx * (targetScale - 1f) / 2f).coerceAtLeast(0f)
                                val maxOffsetY = (heightPx * (targetScale - 1f) / 2f).coerceAtLeast(0f)
                                val targetOffset = Offset(
                                    offsetAnim.value.x.coerceIn(-maxOffsetX, maxOffsetX),
                                    offsetAnim.value.y.coerceIn(-maxOffsetY, maxOffsetY)
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
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Zoom In",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Zoom Percentage Indicator
                    Text(
                        text = "${(currentScale * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (isZoomed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )

                    // Zoom Out Button (-)
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                val targetScale = (scaleAnim.value / 1.5f).coerceIn(1f, 10f)
                                val maxOffsetX = (widthPx * (targetScale - 1f) / 2f).coerceAtLeast(0f)
                                val maxOffsetY = (heightPx * (targetScale - 1f) / 2f).coerceAtLeast(0f)
                                val targetOffset = if (targetScale <= 1.05f) {
                                    Offset.Zero
                                } else {
                                    Offset(
                                        offsetAnim.value.x.coerceIn(-maxOffsetX, maxOffsetX),
                                        offsetAnim.value.y.coerceIn(-maxOffsetY, maxOffsetY)
                                    )
                                }
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
                        },
                        enabled = currentScale > 1.01f,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Remove,
                            contentDescription = "Zoom Out",
                            tint = if (currentScale > 1.01f) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
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
