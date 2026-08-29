package app.quarry.tanvir.info

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.quarry.tanvir.info.data.preferences.ThemeMode
import app.quarry.tanvir.info.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface MainUiState {
    data object Loading : MainUiState
    data class Success(
        val isOnboardingCompleted: Boolean,
        val themeMode: ThemeMode,
        val isDynamicColor: Boolean,
        val isKeepScreenOn: Boolean = false
    ) : MainUiState
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefsRepo = UserPreferencesRepository.getInstance(application)

    val uiState: StateFlow<MainUiState> = combine(
        prefsRepo.isOnboardingCompleted,
        prefsRepo.themeMode,
        prefsRepo.isDynamicColorEnabled,
        prefsRepo.isKeepScreenOn
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val isOnboardingCompleted = args[0] as Boolean
        val themeMode = args[1] as ThemeMode
        val isDynamicColor = args[2] as Boolean
        val isKeepScreenOn = args[3] as Boolean
        MainUiState.Success(
            isOnboardingCompleted = isOnboardingCompleted,
            themeMode = themeMode,
            isDynamicColor = isDynamicColor,
            isKeepScreenOn = isKeepScreenOn
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = MainUiState.Loading
    )

    fun completeOnboarding() {
        viewModelScope.launch {
            prefsRepo.setOnboardingCompleted(true)
        }
    }
}
