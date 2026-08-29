package app.quarry.tanvir.info.ui.home

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.quarry.tanvir.info.domain.app.AppManager
import app.quarry.tanvir.info.domain.app.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AppManagerUiState(
    val apps: List<InstalledApp> = emptyList(),
    val isLoading: Boolean = true,
    val hasUsageAccess: Boolean = false,
    val isSelectionMode: Boolean = false,
    val selectedPackages: Set<String> = emptySet(),
    val detailApp: InstalledApp? = null,
    val showSystemApps: Boolean = true,
    val sortByName: Boolean = false,
    val userMessage: String? = null
) {
    val selectedApps: List<InstalledApp>
        get() = apps.filter { selectedPackages.contains(it.packageName) }

    val selectedBytes: Long
        get() = selectedApps.sumOf { it.size.totalBytes }
}

class AppManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val appManager = AppManager(application)

    private val _uiState = MutableStateFlow(AppManagerUiState())
    val uiState: StateFlow<AppManagerUiState> = _uiState.asStateFlow()

    private val iconCache = mutableMapOf<String, ImageBitmap?>()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val hasAccess = appManager.hasUsageAccess()
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                hasUsageAccess = hasAccess
            )
            val apps = appManager.getInstalledApps(includeSystemApps = _uiState.value.showSystemApps)
            _uiState.value = _uiState.value.copy(
                apps = apps,
                isLoading = false,
                hasUsageAccess = appManager.hasUsageAccess()
            )
        }
    }

    fun getIcon(packageName: String): ImageBitmap? {
        return iconCache.getOrPut(packageName) {
            try {
                drawableToBitmap(
                    getApplication<Application>().packageManager.getApplicationIcon(packageName)
                )?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap? {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
        return try {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    fun setShowSystemApps(show: Boolean) {
        _uiState.value = _uiState.value.copy(showSystemApps = show)
        refresh()
    }

    fun toggleSort() {
        _uiState.value = _uiState.value.copy(sortByName = !_uiState.value.sortByName)
    }

    fun setSelectionMode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            isSelectionMode = enabled,
            selectedPackages = if (enabled) _uiState.value.selectedPackages else emptySet()
        )
    }

    fun toggleSelection(packageName: String) {
        val current = _uiState.value.selectedPackages.toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        _uiState.value = _uiState.value.copy(selectedPackages = current)
    }

    fun selectAll() {
        _uiState.value = _uiState.value.copy(
            selectedPackages = _uiState.value.apps.map { it.packageName }.toSet()
        )
    }

    fun deselectAll() {
        _uiState.value = _uiState.value.copy(selectedPackages = emptySet())
    }

    fun showDetails(app: InstalledApp) {
        _uiState.value = _uiState.value.copy(detailApp = app)
    }

    fun dismissDetails() {
        _uiState.value = _uiState.value.copy(detailApp = null)
    }

    fun openApp(app: InstalledApp) {
        if (!appManager.openApp(app.packageName)) {
            appManager.openAppDetails(app.packageName)
        }
    }

    fun openAppDetails(app: InstalledApp) {
        appManager.openAppDetails(app.packageName)
    }

    fun uninstall(app: InstalledApp) {
        appManager.uninstall(app.packageName)
    }

    fun uninstallSelected() {
        val selected = _uiState.value.selectedPackages.toList()
        if (selected.isEmpty()) return
        selected.forEach { appManager.uninstall(it) }
        _uiState.value = _uiState.value.copy(
            selectedPackages = emptySet(),
            isSelectionMode = false,
            userMessage = "Uninstall request sent for ${selected.size} app(s)"
        )
    }

    fun openUsageAccessSettings() {
        appManager.openUsageAccessSettings()
    }

    fun clearUserMessage() {
        _uiState.value = _uiState.value.copy(userMessage = null)
    }
}
