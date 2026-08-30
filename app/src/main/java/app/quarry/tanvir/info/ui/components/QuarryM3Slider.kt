package app.quarry.tanvir.info.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive styled Slider matching Android system volume/haptic slider design.
 * Features a thick pill track, clean gap spacing, and a sleek vertical pill thumb.
 */
@Composable
fun QuarryM3Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 1f..100f,
    onValueChangeFinished: (() -> Unit)? = null,
    trackHeight: Dp = 16.dp,
    thumbHeight: Dp = 32.dp,
    thumbWidth: Dp = 4.dp,
    gap: Dp = 4.dp,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
    thumbColor: Color = MaterialTheme.colorScheme.primary
) {
    val rangeSize = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val fraction = ((value - valueRange.start) / rangeSize).coerceIn(0f, 1f)

    val updatedOnValueChange by rememberUpdatedState(onValueChange)
    val updatedOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .pointerInput(valueRange) {
                detectTapGestures(
                    onPress = { offset ->
                        val newFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        val newValue = valueRange.start + newFraction * rangeSize
                        updatedOnValueChange(newValue)
                        tryAwaitRelease()
                        updatedOnValueChangeFinished?.invoke()
                    }
                )
            }
            .pointerInput(valueRange) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val newFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        val newValue = valueRange.start + newFraction * rangeSize
                        updatedOnValueChange(newValue)
                    },
                    onDragEnd = {
                        updatedOnValueChangeFinished?.invoke()
                    },
                    onDragCancel = {
                        updatedOnValueChangeFinished?.invoke()
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val newFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        val newValue = valueRange.start + newFraction * rangeSize
                        updatedOnValueChange(newValue)
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val trackHeightPx = with(density) { trackHeight.toPx() }
        val thumbWidthPx = with(density) { thumbWidth.toPx() }
        val thumbHeightPx = with(density) { thumbHeight.toPx() }
        val gapPx = with(density) { gap.toPx() }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            val centerY = size.height / 2f
            val trackRadius = CornerRadius(trackHeightPx / 2f, trackHeightPx / 2f)
            val thumbRadius = CornerRadius(thumbWidthPx / 2f, thumbWidthPx / 2f)

            val minThumbX = thumbWidthPx / 2f
            val maxThumbX = size.width - thumbWidthPx / 2f
            val thumbCenterX = minThumbX + fraction * (maxThumbX - minThumbX)

            // Active Track (Left)
            val activeEnd = (thumbCenterX - thumbWidthPx / 2f - gapPx).coerceAtLeast(0f)
            if (activeEnd > 0f) {
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(0f, centerY - trackHeightPx / 2f),
                    size = Size(activeEnd, trackHeightPx),
                    cornerRadius = trackRadius
                )
            }

            // Inactive Track (Right)
            val inactiveStart = (thumbCenterX + thumbWidthPx / 2f + gapPx).coerceAtMost(size.width)
            if (inactiveStart < size.width) {
                drawRoundRect(
                    color = inactiveColor,
                    topLeft = Offset(inactiveStart, centerY - trackHeightPx / 2f),
                    size = Size(size.width - inactiveStart, trackHeightPx),
                    cornerRadius = trackRadius
                )
            }

            // Thumb (Vertical Bar)
            val thumbLeft = thumbCenterX - thumbWidthPx / 2f
            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(thumbLeft, centerY - thumbHeightPx / 2f),
                size = Size(thumbWidthPx, thumbHeightPx),
                cornerRadius = thumbRadius
            )
        }
    }
}
