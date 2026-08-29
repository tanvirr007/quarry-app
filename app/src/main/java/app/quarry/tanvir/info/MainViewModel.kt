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
        val isDynamicColor: Boolean
    ) : MainUiState
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefsRepo = UserPreferencesRepository.getInstance(application)

    val uiState: StateFlow<MainUiState> = combine(
        prefsRepo.isOnboardingCompleted,
        prefsRepo.themeMode,
        prefsRepo.isDynamicColorEnabled
    ) { isOnboardingCompleted, themeMode, isDynamicColor ->
        MainUiState.Success(
            isOnboardingCompleted = isOnboardingCompleted,
            themeMode = themeMode,
            isDynamicColor = isDynamicColor
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
