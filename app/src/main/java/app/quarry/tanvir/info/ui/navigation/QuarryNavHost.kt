package app.quarry.tanvir.info.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import app.quarry.tanvir.info.ui.cleanup.CleanupScreen
import app.quarry.tanvir.info.ui.explore.ExploreScreen
import app.quarry.tanvir.info.ui.home.HomeScreen
import app.quarry.tanvir.info.ui.settings.SettingsScreen

/**
 * Directional slide + fade transition for bottom-nav sibling destinations.
 * Determines slide direction from tab index: higher index = slide from right, lower = from left.
 */
private const val TRANSITION_DURATION = 300
private const val SLIDE_OFFSET_FRACTION = 4 // 1/4 = 25% of width

private val tabIndexMap = Screen.bottomNavItems
    .mapIndexed { index, screen -> screen.route to index }
    .toMap()

private fun tabIndex(route: String?): Int = tabIndexMap[route] ?: -1

@Composable
fun QuarryNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier,
        enterTransition = {
            val direction = tabIndex(targetState.destination.route) - tabIndex(initialState.destination.route)
            slideInHorizontally(
                initialOffsetX = { fullWidth -> if (direction >= 0) fullWidth / SLIDE_OFFSET_FRACTION else -fullWidth / SLIDE_OFFSET_FRACTION },
                animationSpec = tween(durationMillis = TRANSITION_DURATION, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(durationMillis = TRANSITION_DURATION))
        },
        exitTransition = {
            val direction = tabIndex(targetState.destination.route) - tabIndex(initialState.destination.route)
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> if (direction >= 0) -fullWidth / SLIDE_OFFSET_FRACTION else fullWidth / SLIDE_OFFSET_FRACTION },
                animationSpec = tween(durationMillis = TRANSITION_DURATION, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(durationMillis = TRANSITION_DURATION))
        },
        popEnterTransition = {
            val direction = tabIndex(targetState.destination.route) - tabIndex(initialState.destination.route)
            slideInHorizontally(
                initialOffsetX = { fullWidth -> if (direction >= 0) fullWidth / SLIDE_OFFSET_FRACTION else -fullWidth / SLIDE_OFFSET_FRACTION },
                animationSpec = tween(durationMillis = TRANSITION_DURATION, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(durationMillis = TRANSITION_DURATION))
        },
        popExitTransition = {
            val direction = tabIndex(targetState.destination.route) - tabIndex(initialState.destination.route)
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> if (direction >= 0) -fullWidth / SLIDE_OFFSET_FRACTION else fullWidth / SLIDE_OFFSET_FRACTION },
                animationSpec = tween(durationMillis = TRANSITION_DURATION, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(durationMillis = TRANSITION_DURATION))
        }
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToExplore = {
                    navController.navigate(Screen.Explore.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
        composable(Screen.Explore.route) {
            ExploreScreen()
        }
        composable(Screen.Cleanup.route) {
            CleanupScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
    }
}

