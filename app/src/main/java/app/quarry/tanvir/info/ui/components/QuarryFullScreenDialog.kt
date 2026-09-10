package app.quarry.tanvir.info.ui.components

import android.app.Activity
import android.content.ContextWrapper
import android.view.View
import android.view.Window
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import app.quarry.tanvir.info.ui.theme.LocalDarkTheme
import kotlinx.coroutines.flow.filter

/** Slide + fade transition duration matching bottom-nav transitions. */
private const val TRANSITION_DURATION = 300

/** Slide offset as a fraction of full width (1/4 = 25%). */
private const val SLIDE_OFFSET_FRACTION = 4

/**
 * CompositionLocal providing an animated dismiss callback.
 * Dialog content should call this instead of the raw onDismiss to trigger the exit animation
 * before the actual dismissal fires.
 */
val LocalAnimatedDismiss = compositionLocalOf<() -> Unit> { error("No animated dismiss provided") }

/**
 * Full-screen dialog wrapper with optional slide + fade page-transition animation.
 *
 * @param animated When true (default), the dialog content slides in from the right on entry
 *   and slides out to the right on dismiss, matching the bottom-nav transitions. When false,
 *   the dialog behaves identically to the original non-animated version.
 */
@Composable
fun QuarryFullScreenDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false
    ),
    isDarkTheme: Boolean = LocalDarkTheme.current,
    animated: Boolean = true,
    content: @Composable () -> Unit
) {
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)

    if (animated) {
        AnimatedFullScreenDialog(currentOnDismissRequest, properties, isDarkTheme, content)
    } else {
        StaticFullScreenDialog(currentOnDismissRequest, properties, isDarkTheme, content)
    }
}

/**
 * Animated variant: slides content in from the right, plays exit animation before dismissal.
 */
@Composable
private fun AnimatedFullScreenDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties,
    isDarkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    var pendingDismiss by remember { mutableStateOf(false) }

    val animatedDismiss: () -> Unit = remember {
        {
            if (!pendingDismiss) {
                visibleState.targetState = false
                pendingDismiss = true
            }
        }
    }

    // Trigger actual dismissal after exit animation settles
    LaunchedEffect(Unit) {
        snapshotFlow { visibleState.isIdle && !visibleState.currentState && pendingDismiss }
            .filter { it }
            .collect { onDismissRequest() }
    }

    Dialog(
        onDismissRequest = animatedDismiss,
        properties = properties
    ) {
        val view = LocalView.current
        if (!view.isInEditMode) {
            SideEffect {
                val window = findWindow(view)
                if (window != null) {
                    window.statusBarColor = android.graphics.Color.TRANSPARENT
                    window.navigationBarColor = android.graphics.Color.TRANSPARENT
                    // Remove background scrim so the slide reveals the parent screen behind
                    window.setDimAmount(0f)
                    // Disable default Dialog window enter/exit animation
                    window.setWindowAnimations(0)
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.isAppearanceLightStatusBars = !isDarkTheme
                    insetsController.isAppearanceLightNavigationBars = !isDarkTheme
                }
            }
        }

        CompositionLocalProvider(LocalAnimatedDismiss provides animatedDismiss) {
            AnimatedVisibility(
                visibleState = visibleState,
                enter = slideInHorizontally(
                    initialOffsetX = { fullWidth -> fullWidth / SLIDE_OFFSET_FRACTION },
                    animationSpec = tween(
                        durationMillis = TRANSITION_DURATION,
                        easing = FastOutSlowInEasing
                    )
                ) + fadeIn(
                    animationSpec = tween(durationMillis = TRANSITION_DURATION)
                ),
                exit = slideOutHorizontally(
                    targetOffsetX = { fullWidth -> fullWidth / SLIDE_OFFSET_FRACTION },
                    animationSpec = tween(
                        durationMillis = TRANSITION_DURATION,
                        easing = FastOutSlowInEasing
                    )
                ) + fadeOut(
                    animationSpec = tween(durationMillis = TRANSITION_DURATION)
                )
            ) {
                content()
            }
        }
    }
}

/**
 * Static variant: no enter/exit animation, preserves original Dialog behaviour.
 */
@Composable
private fun StaticFullScreenDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties,
    isDarkTheme: Boolean,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        val view = LocalView.current
        if (!view.isInEditMode) {
            SideEffect {
                val window = findWindow(view)
                if (window != null) {
                    window.statusBarColor = android.graphics.Color.TRANSPARENT
                    window.navigationBarColor = android.graphics.Color.TRANSPARENT
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.isAppearanceLightStatusBars = !isDarkTheme
                    insetsController.isAppearanceLightNavigationBars = !isDarkTheme
                }
            }
        }
        // Provide a direct dismiss for content that reads LocalAnimatedDismiss
        CompositionLocalProvider(LocalAnimatedDismiss provides onDismissRequest) {
            content()
        }
    }
}

internal fun findWindow(view: View): Window? {
    var parent = view.parent
    while (parent != null) {
        if (parent is DialogWindowProvider) {
            return parent.window
        }
        parent = parent.parent
    }
    var context = view.context
    while (context is ContextWrapper) {
        if (context is Activity) {
            return context.window
        }
        context = context.baseContext
    }
    return null
}
