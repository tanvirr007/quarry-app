package app.quarry.tanvir.info.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/**
 * Creates a [NestedScrollConnection] that consumes unconsumed scroll deltas and fling velocities.
 * This prevents parent containers such as ModalBottomSheet from being dragged upward into the
 * top toolbar or jittering/flickering when scrolling to the ends of inner lists.
 */
@Composable
fun rememberBlockNestedScrollConnection(): NestedScrollConnection {
    return remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // Absorb leftover scroll deltas so parent ModalBottomSheet does not pull or jitter
                return available
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                // Absorb leftover fling velocities so sheet does not oscillate or fling upwards
                return available
            }
        }
    }
}
