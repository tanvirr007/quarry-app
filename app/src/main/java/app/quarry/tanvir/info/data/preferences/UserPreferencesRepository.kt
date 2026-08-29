package app.quarry.tanvir.info.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

enum class ThemeMode(val displayName: String) {
    SYSTEM("System Default"),
    LIGHT("Light Mode"),
    DARK("Dark Mode")
}

class UserPreferencesRepository private constructor(private val context: Context) {

    private val THEME_KEY = stringPreferencesKey("theme_mode")
    private val DYNAMIC_COLOR_KEY = booleanPreferencesKey("dynamic_color_enabled")
    private val ONBOARDING_COMPLETED_KEY = booleanPreferencesKey("onboarding_completed")
    private val BIOMETRIC_AUTH_KEY = booleanPreferencesKey("biometric_auth_enabled")
    private val DELETE_COUNTDOWN_KEY = intPreferencesKey("delete_countdown_seconds")
    private val EXCLUDED_FOLDERS_KEY = stringSetPreferencesKey("excluded_folders")
    private val SCAN_HIDDEN_FILES_KEY = booleanPreferencesKey("scan_hidden_files")

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        val raw = preferences[THEME_KEY] ?: ThemeMode.SYSTEM.name
        try {
            ThemeMode.valueOf(raw)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
    }

    val isDynamicColorEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[DYNAMIC_COLOR_KEY] ?: true
    }

    val scanHiddenFiles: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SCAN_HIDDEN_FILES_KEY] ?: false
    }

    val isOnboardingCompleted: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ONBOARDING_COMPLETED_KEY] ?: false
    }

    val isBiometricAuthEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[BIOMETRIC_AUTH_KEY] ?: true
    }

    val deleteCountdownSeconds: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[DELETE_COUNTDOWN_KEY] ?: 5
    }

    val excludedFolders: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[EXCLUDED_FOLDERS_KEY] ?: emptySet()
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[THEME_KEY] = mode.name
        }
    }

    suspend fun setDynamicColorEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DYNAMIC_COLOR_KEY] = enabled
        }
    }

    suspend fun setScanHiddenFiles(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SCAN_HIDDEN_FILES_KEY] = enabled
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETED_KEY] = completed
        }
    }

    suspend fun setBiometricAuthEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[BIOMETRIC_AUTH_KEY] = enabled
        }
    }

    suspend fun setDeleteCountdownSeconds(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[DELETE_COUNTDOWN_KEY] = seconds.coerceIn(0, 10)
        }
    }

    suspend fun addExcludedFolder(path: String) {
        val trimmed = path.trim()
        if (trimmed.isBlank()) return
        context.dataStore.edit { preferences ->
            val current = preferences[EXCLUDED_FOLDERS_KEY] ?: emptySet()
            preferences[EXCLUDED_FOLDERS_KEY] = current + trimmed
        }
    }

    suspend fun addExcludedFolders(paths: Collection<String>) {
        val cleaned = paths.map { it.trim() }.filter { it.isNotBlank() }
        if (cleaned.isEmpty()) return
        context.dataStore.edit { preferences ->
            val current = preferences[EXCLUDED_FOLDERS_KEY] ?: emptySet()
            preferences[EXCLUDED_FOLDERS_KEY] = current + cleaned
        }
    }

    suspend fun removeExcludedFolder(path: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[EXCLUDED_FOLDERS_KEY] ?: emptySet()
            preferences[EXCLUDED_FOLDERS_KEY] = current - path
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: UserPreferencesRepository? = null

        fun getInstance(context: Context): UserPreferencesRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = UserPreferencesRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
