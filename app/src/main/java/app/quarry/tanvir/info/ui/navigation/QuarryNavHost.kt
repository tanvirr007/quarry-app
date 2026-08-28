package app.quarry.tanvir.info.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import app.quarry.tanvir.info.ui.cleanup.CleanupScreen
import app.quarry.tanvir.info.ui.explore.ExploreScreen
import app.quarry.tanvir.info.ui.home.HomeScreen
import app.quarry.tanvir.info.ui.settings.SettingsScreen

@Composable
fun QuarryNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen()
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
