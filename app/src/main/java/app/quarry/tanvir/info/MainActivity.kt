package app.quarry.tanvir.info

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.quarry.tanvir.info.ui.navigation.QuarryBottomBar
import app.quarry.tanvir.info.ui.navigation.QuarryNavHost
import app.quarry.tanvir.info.ui.navigation.Screen
import app.quarry.tanvir.info.ui.theme.QuarryTheme

import androidx.fragment.app.FragmentActivity

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.quarry.tanvir.info.data.preferences.ThemeMode
import app.quarry.tanvir.info.data.preferences.UserPreferencesRepository

import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import app.quarry.tanvir.info.ui.onboarding.OnboardingDialog

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefsRepo = UserPreferencesRepository.getInstance(applicationContext)

        setContent {
            val themeMode by prefsRepo.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val isOnboardingCompleted by prefsRepo.isOnboardingCompleted.collectAsStateWithLifecycle(initialValue = true)
            val scope = rememberCoroutineScope()

            QuarryTheme(themeMode = themeMode) {
                QuarryMainApp()

                if (!isOnboardingCompleted) {
                    OnboardingDialog(
                        onDismiss = {
                            scope.launch { prefsRepo.setOnboardingCompleted(true) }
                        },
                        onCompleted = {
                            scope.launch { prefsRepo.setOnboardingCompleted(true) }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuarryMainApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val currentScreen = Screen.bottomNavItems.find { it.route == currentRoute } ?: Screen.Home

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(currentScreen.titleRes),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            QuarryBottomBar(navController = navController)
        }
    ) { innerPadding ->
        QuarryNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}
