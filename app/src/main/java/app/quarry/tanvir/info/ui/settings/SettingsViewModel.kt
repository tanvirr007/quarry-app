package app.quarry.tanvir.info.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.fragment.app.FragmentActivity
import app.quarry.tanvir.info.data.preferences.ThemeMode
import app.quarry.tanvir.info.data.preferences.UserPreferencesRepository
import app.quarry.tanvir.info.domain.security.BiometricSecurityManager
import app.quarry.tanvir.info.domain.volume.StorageVolumeInfo
import app.quarry.tanvir.info.domain.volume.StorageVolumeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val isBiometricEnabled: Boolean = true,
    val deleteCountdownSeconds: Int = 5,
    val excludedFolders: Set<String> = emptySet(),
    val scanHiddenFiles: Boolean = false,
    val detectedVolumes: List<StorageVolumeInfo> = emptyList(),
    val isThemeDialogVisible: Boolean = false,
    val isVolumesDialogVisible: Boolean = false,
    val isExclusionsDialogVisible: Boolean = false,
    val userMessage: String? = null
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepo = UserPreferencesRepository.getInstance(application)
    private val volumeManager = StorageVolumeManager(application)
    private val securityManager = BiometricSecurityManager(application)

    private val _detectedVolumes = MutableStateFlow<List<StorageVolumeInfo>>(emptyList())
    private val _isThemeDialogVisible = MutableStateFlow(false)
    private val _isVolumesDialogVisible = MutableStateFlow(false)
    private val _isExclusionsDialogVisible = MutableStateFlow(false)
    private val _userMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            prefsRepo.themeMode,
            prefsRepo.isBiometricAuthEnabled,
            prefsRepo.deleteCountdownSeconds,
            prefsRepo.excludedFolders,
            prefsRepo.scanHiddenFiles
        ) { theme, biometric, countdown, exclusions, scanHidden ->
            SettingsUiState(
                themeMode = theme,
                isBiometricEnabled = biometric,
                deleteCountdownSeconds = countdown,
                excludedFolders = exclusions,
                scanHiddenFiles = scanHidden
            )
        },
        _detectedVolumes,
        combine(
            _isThemeDialogVisible,
            _isVolumesDialogVisible,
            _isExclusionsDialogVisible
        ) { themeD, volD, exclD ->
            listOf(themeD, volD, exclD)
        },
        _userMessage
    ) { baseState, volumes, dialogStates, userMsg ->
        baseState.copy(
            detectedVolumes = volumes,
            isThemeDialogVisible = dialogStates[0],
            isVolumesDialogVisible = dialogStates[1],
            isExclusionsDialogVisible = dialogStates[2],
            userMessage = userMsg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    init {
        loadVolumes()
    }

    fun loadVolumes() {
        _detectedVolumes.value = volumeManager.getDetectedVolumes()
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            prefsRepo.setThemeMode(mode)
            _isThemeDialogVisible.value = false
        }
    }

    fun toggleBiometricProtection(activity: FragmentActivity?, enable: Boolean) {
        if (activity == null) {
            _userMessage.value = "Unable to start authentication"
            return
        }

        if (enable) {
            if (!securityManager.canAuthenticate(activity)) {
                _userMessage.value = "No screen lock or biometric enrolled. Please configure a PIN, pattern, or fingerprint in Android Settings."
                return
            }

            securityManager.authenticate(
                activity = activity,
                title = "Enable Protection",
                subtitle = "Authenticate to turn on Biometric & PIN Protection",
                onSuccess = {
                    viewModelScope.launch {
                        prefsRepo.setBiometricAuthEnabled(true)
                        _userMessage.value = "Biometric & PIN protection enabled"
                    }
                },
                onError = { error ->
                    _userMessage.value = error
                }
            )
        } else {
            securityManager.authenticate(
                activity = activity,
                title = "Disable Protection",
                subtitle = "Authenticate to turn off Biometric & PIN Protection",
                onSuccess = {
                    viewModelScope.launch {
                        prefsRepo.setBiometricAuthEnabled(false)
                        _userMessage.value = "Biometric & PIN protection disabled"
                    }
                },
                onError = { error ->
                    _userMessage.value = error
                }
            )
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun setScanHiddenFiles(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setScanHiddenFiles(enabled)
        }
    }

    fun addExclusion(path: String) {
        viewModelScope.launch {
            prefsRepo.addExcludedFolder(path)
        }
    }

    fun removeExclusion(path: String) {
        viewModelScope.launch {
            prefsRepo.removeExcludedFolder(path)
        }
    }

    fun showThemeDialog() { _isThemeDialogVisible.value = true }
    fun hideThemeDialog() { _isThemeDialogVisible.value = false }

    fun showVolumesDialog() {
        loadVolumes()
        _isVolumesDialogVisible.value = true
    }
    fun hideVolumesDialog() { _isVolumesDialogVisible.value = false }

    fun showExclusionsDialog() { _isExclusionsDialogVisible.value = true }
    fun hideExclusionsDialog() { _isExclusionsDialogVisible.value = false }
}
