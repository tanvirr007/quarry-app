package app.quarry.tanvir.info.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import app.quarry.tanvir.info.R

sealed class Screen(
    val route: String,
    @StringRes val titleRes: Int,
    val icon: ImageVector
) {
    data object Home : Screen(
        route = "home",
        titleRes = R.string.nav_home,
        icon = Icons.Rounded.Home
    )

    data object Explore : Screen(
        route = "explore",
        titleRes = R.string.nav_explore,
        icon = Icons.Rounded.Folder
    )

    data object Cleanup : Screen(
        route = "cleanup",
        titleRes = R.string.nav_cleanup,
        icon = Icons.Rounded.CleaningServices
    )

    data object Settings : Screen(
        route = "settings",
        titleRes = R.string.nav_settings,
        icon = Icons.Rounded.Settings
    )

    companion object {
        val bottomNavItems = listOf(Home, Explore, Cleanup, Settings)
    }
}
