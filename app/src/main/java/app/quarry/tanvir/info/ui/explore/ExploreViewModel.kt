package app.quarry.tanvir.info.ui.explore

import android.app.Application
import android.os.Environment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.quarry.tanvir.info.data.database.FileEntity
import app.quarry.tanvir.info.domain.file.FileOperationsManager
import app.quarry.tanvir.info.domain.model.StorageCategory
import app.quarry.tanvir.info.domain.scanner.ScanRepository
import app.quarry.tanvir.info.domain.security.BiometricSecurityManager
import app.quarry.tanvir.info.domain.treemap.TreemapEngine
import app.quarry.tanvir.info.domain.treemap.TreemapNode
import app.quarry.tanvir.info.domain.treemap.TreemapRect
import app.quarry.tanvir.info.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class ExploreViewMode(val title: String) {
    TREEMAP("Treemap"),
    LIST("List"),
    LARGEST("Largest"),
    TYPES("Types"),
    FOLDERS("Folders")
}

enum class FileSortOrder(val displayName: String) {
    SIZE_DESC("Size: Largest first"),
    SIZE_ASC("Size: Smallest first"),
    NAME_ASC("Name: A → Z"),
    NAME_DESC("Name: Z → A"),
    DATE_DESC("Date: Newest first"),
    DATE_ASC("Date: Oldest first");

    fun comparator(keepDirectoriesFirst: Boolean = false): Comparator<FileEntity> {
        val baseComparator = when (this) {
            SIZE_DESC -> compareByDescending<FileEntity> { it.size }
            SIZE_ASC -> compareBy<FileEntity> { it.size }
            NAME_ASC -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            NAME_DESC -> compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name }
            DATE_DESC -> compareByDescending { it.lastModified }
            DATE_ASC -> compareBy { it.lastModified }
        }
        return if (keepDirectoriesFirst) {
            compareByDescending<FileEntity> { it.isDirectory }.then(baseComparator)
        } else {
            baseComparator
        }
    }
}

private data class ExploreBaseState(
    val path: String,
    val mode: ExploreViewMode,
    val query: String,
    val category: StorageCategory?,
    val sortOrder: FileSortOrder
)

private data class DialogState(
    val selected: Set<String>,
    val details: FileEntity?,
    val rename: FileEntity?,
    val deleteCandidates: List<FileEntity>,
    val deleteVisible: Boolean
)

data class ExploreUiState(
    val currentPath: String = Environment.getExternalStorageDirectory().absolutePath,
    val viewMode: ExploreViewMode = ExploreViewMode.TREEMAP,
    val searchQuery: String = "",
    val selectedCategory: StorageCategory? = null,
    val sortOrder: FileSortOrder = FileSortOrder.SIZE_DESC,
    val showHiddenFiles: Boolean = false,
    val isBiometricEnabled: Boolean = true,
    val currentDirectoryFiles: List<FileEntity> = emptyList(),
    val treemapNodes: List<TreemapNode> = emptyList(),
    val largestFiles: List<FileEntity> = emptyList(),
    val searchResults: List<FileEntity> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val activeDetailsFile: FileEntity? = null,
    val activeRenameFile: FileEntity? = null,
    val activeDeleteCandidates: List<FileEntity> = emptyList(),
    val isDeleteCountdownVisible: Boolean = false,
    val userMessage: String? = null
)

class ExploreViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ScanRepository.getInstance(application)
    private val prefsRepo = UserPreferencesRepository.getInstance(application)
    private val fileOps = FileOperationsManager(application, repository)
    private val securityManager = BiometricSecurityManager(application)

    private val defaultRootPath = Environment.getExternalStorageDirectory().absolutePath

    private val _currentPath = MutableStateFlow(defaultRootPath)
    private val _viewMode = MutableStateFlow(ExploreViewMode.TREEMAP)
    private val _searchQuery = MutableStateFlow("")
    private val _selectedCategory = MutableStateFlow<StorageCategory?>(null)
    private val _sortOrder = MutableStateFlow(FileSortOrder.SIZE_DESC)
    private val _selectedPaths = MutableStateFlow<Set<String>>(emptySet())
    private val _activeDetailsFile = MutableStateFlow<FileEntity?>(null)
    private val _activeRenameFile = MutableStateFlow<FileEntity?>(null)
    private val _activeDeleteCandidates = MutableStateFlow<List<FileEntity>>(emptyList())
    private val _isDeleteCountdownVisible = MutableStateFlow(false)
    private val _userMessage = MutableStateFlow<String?>(null)

    private fun filterHiddenAndExcluded(
        files: List<FileEntity>,
        showHidden: Boolean,
        excluded: Set<String>
    ): List<FileEntity> {
        return files.filter { entity ->
            // Hide zero-byte folders everywhere in Explore (treemap + lists) so empty
            // directories never consume layout area or list rows.
            if (entity.isDirectory && entity.size == 0L) return@filter false
            val hiddenOk = showHidden || !entity.name.startsWith(".")
            val excludedOk = !app.quarry.tanvir.info.domain.scanner.ExclusionMatcher.isExcluded(entity.path, excluded) &&
                    !app.quarry.tanvir.info.domain.scanner.ExclusionMatcher.isExcluded(entity.parentPath ?: "", excluded)
            hiddenOk && excludedOk
        }
    }

    private val filteredLargestFiles: StateFlow<List<FileEntity>> = combine(
        repository.getLargestFiles(100),
        prefsRepo.scanHiddenFiles,
        prefsRepo.excludedFolders,
        _sortOrder
    ) { largest, showHidden, excluded, sort ->
        val filtered = filterHiddenAndExcluded(largest, showHidden, excluded)
        filtered.sortedWith(sort.comparator(keepDirectoriesFirst = false))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allFiles: StateFlow<List<FileEntity>> = combine(
        repository.getAllFiles(),
        prefsRepo.scanHiddenFiles,
        prefsRepo.excludedFolders,
        _sortOrder
    ) { files, showHidden, excluded, sort ->
        val filtered = filterHiddenAndExcluded(files, showHidden, excluded)
        filtered.sortedWith(sort.comparator(keepDirectoriesFirst = true))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val searchResultsFlow: StateFlow<List<FileEntity>> = combine(
        allFiles,
        _searchQuery,
        _selectedCategory,
        _sortOrder,
        _currentPath
    ) { files, query, category, sort, currentPath ->
        if (query.isBlank()) {
            emptyList()
        } else {
            val matching = files.filter { file ->
                val matchesCategory = category == null || (!file.isDirectory && StorageCategory.fromExtension(file.extension) == category)
                val matchesQuery = app.quarry.tanvir.info.domain.model.SearchMatcher.matches(file.name, file.path, query)
                matchesCategory && matchesQuery
            }

            matching.sortedWith(
                compareByDescending<FileEntity> { it.path.startsWith(currentPath) }
                    .then(sort.comparator(keepDirectoriesFirst = true))
            )
        }
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val uiState: StateFlow<ExploreUiState> = combine(
        combine(_currentPath, _viewMode, _searchQuery, _selectedCategory, _sortOrder) { path, mode, query, cat, sort ->
            ExploreBaseState(path, mode, query, cat, sort)
        },
        combine(prefsRepo.scanHiddenFiles, prefsRepo.isBiometricAuthEnabled) { hidden, biometric ->
            Pair(hidden, biometric)
        },
        combine(_selectedPaths, _activeDetailsFile, _activeRenameFile, _activeDeleteCandidates, _isDeleteCountdownVisible) { selected, details, rename, deleteCandidates, deleteVisible ->
            DialogState(selected, details, rename, deleteCandidates, deleteVisible)
        },
        combine(_userMessage, filteredLargestFiles, searchResultsFlow) { msg, largest, searchResults ->
            Triple(msg, largest, searchResults)
        }
    ) { base, prefs, dialogs, msgLargestSearch ->
        ExploreUiState(
            currentPath = base.path,
            viewMode = base.mode,
            searchQuery = base.query,
            selectedCategory = base.category,
            sortOrder = base.sortOrder,
            showHiddenFiles = prefs.first,
            isBiometricEnabled = prefs.second,
            selectedPaths = dialogs.selected,
            isSelectionMode = dialogs.selected.isNotEmpty(),
            activeDetailsFile = dialogs.details,
            activeRenameFile = dialogs.rename,
            activeDeleteCandidates = dialogs.deleteCandidates,
            isDeleteCountdownVisible = dialogs.deleteVisible,
            userMessage = msgLargestSearch.first,
            largestFiles = msgLargestSearch.second,
            searchResults = msgLargestSearch.third
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ExploreUiState()
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val directoryFiles: StateFlow<List<FileEntity>> = combine(
        _currentPath.flatMapLatest { path -> repository.getChildren(path) },
        prefsRepo.scanHiddenFiles,
        prefsRepo.excludedFolders,
        _sortOrder
    ) { children, showHidden, excluded, sort ->
        val filtered = filterHiddenAndExcluded(children, showHidden, excluded)
        filtered.sortedWith(sort.comparator(keepDirectoriesFirst = true))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allCategorizedFiles: StateFlow<List<FileEntity>> = combine(
        repository.getAllNonDirectoryFiles(),
        prefsRepo.scanHiddenFiles,
        prefsRepo.excludedFolders,
        _sortOrder
    ) { files, showHidden, excluded, sort ->
        val filtered = filterHiddenAndExcluded(files, showHidden, excluded)
        filtered.sortedWith(sort.comparator(keepDirectoriesFirst = false))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _canvasBounds = MutableStateFlow(Pair(1000f, 1000f))

    val treemapLayoutNodes: StateFlow<List<TreemapNode>> = combine(
        directoryFiles,
        _currentPath,
        _canvasBounds
    ) { visibleChildren, path, (widthPx, heightPx) ->
        if (visibleChildren.isEmpty()) {
            emptyList()
        } else {
            val w = if (widthPx > 0f) widthPx else 1000f
            val h = if (heightPx > 0f) heightPx else 1000f
            val tree = TreemapEngine.buildTree(visibleChildren, path)
            TreemapEngine.layoutSquarified(
                items = tree.children,
                bounds = TreemapRect(0f, 0f, w, h)
            )
        }
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setViewMode(mode: ExploreViewMode) {
        _viewMode.value = mode
        if (mode == ExploreViewMode.TREEMAP) {
            _searchQuery.value = ""
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedCategory(category: StorageCategory?) {
        _selectedCategory.value = category
    }

    fun setSortOrder(order: FileSortOrder) {
        _sortOrder.value = order
    }

    fun navigateToDirectory(path: String) {
        _currentPath.value = path
    }

    fun navigateUp(): Boolean {
        val current = _currentPath.value
        if (current == defaultRootPath || current == "/" || current.isEmpty()) return false
        val parent = File(current).parent ?: defaultRootPath
        if (parent.startsWith(defaultRootPath) || parent == defaultRootPath) {
            navigateToDirectory(parent)
            return true
        }
        return false
    }

    fun toggleShowHiddenFiles() {
        viewModelScope.launch {
            val current = try { prefsRepo.scanHiddenFiles.first() } catch (e: Exception) { false }
            prefsRepo.setScanHiddenFiles(!current)
        }
    }

    fun setShowHiddenFiles(show: Boolean) {
        viewModelScope.launch {
            prefsRepo.setScanHiddenFiles(show)
        }
    }

    fun recalculateTreemap(widthPx: Float, heightPx: Float) {
        if (widthPx <= 0f || heightPx <= 0f) return
        val current = _canvasBounds.value
        if (kotlin.math.abs(current.first - widthPx) > 1f || kotlin.math.abs(current.second - heightPx) > 1f) {
            _canvasBounds.value = Pair(widthPx, heightPx)
        }
    }

    fun toggleSelection(path: String) {
        val current = _selectedPaths.value.toMutableSet()
        if (current.contains(path)) {
            current.remove(path)
        } else {
            current.add(path)
        }
        _selectedPaths.value = current
    }

    fun selectAll() {
        val all = directoryFiles.value.map { it.path }.toSet()
        _selectedPaths.value = all
    }

    fun clearSelection() {
        _selectedPaths.value = emptySet()
    }

    fun showDetails(file: FileEntity) {
        _activeDetailsFile.value = file
    }

    fun hideDetails() {
        _activeDetailsFile.value = null
    }

    fun startRename(file: FileEntity) {
        _activeRenameFile.value = file
    }

    fun dismissRename() {
        _activeRenameFile.value = null
    }

    fun executeRename(
        activity: FragmentActivity?,
        file: FileEntity,
        newName: String
    ) {
        val performRename: () -> Unit = {
            viewModelScope.launch {
                val result = fileOps.renameFile(file.path, newName)
                if (result.isSuccess) {
                    _userMessage.value = "Renamed to $newName"
                    _activeRenameFile.value = null
                    _activeDetailsFile.value = null
                } else {
                    _userMessage.value = result.exceptionOrNull()?.message ?: "Rename failed"
                }
            }
        }

        viewModelScope.launch {
            val isAuthEnabled = prefsRepo.isBiometricAuthEnabled.first()
            if (!isAuthEnabled) {
                performRename()
            } else {
                if (activity == null) {
                    _userMessage.value = "Unable to start authentication"
                    return@launch
                }
                securityManager.authenticate(
                    activity = activity,
                    title = "Confirm File Rename",
                    subtitle = "Authenticate to rename ${file.name}",
                    onSuccess = performRename,
                    onError = { error ->
                        _userMessage.value = error
                    }
                )
            }
        }
    }

    fun promptDeleteSingle(file: FileEntity) {
        _activeDeleteCandidates.value = listOf(file)
        _isDeleteCountdownVisible.value = true
    }

    fun promptDeleteSelected() {
        val paths = _selectedPaths.value
        val files = directoryFiles.value.filter { paths.contains(it.path) }
        if (files.isNotEmpty()) {
            _activeDeleteCandidates.value = files
            _isDeleteCountdownVisible.value = true
        }
    }

    fun dismissDeleteDialog() {
        _isDeleteCountdownVisible.value = false
        _activeDeleteCandidates.value = emptyList()
    }

    fun executeAuthenticatedDelete(
        activity: FragmentActivity?,
        candidates: List<FileEntity>
    ) {
        val performDelete: () -> Unit = {
            viewModelScope.launch {
                val paths = candidates.map { it.path }
                val results = fileOps.bulkDelete(paths)
                val successCount = results.count { it.value }
                _userMessage.value = "Deleted $successCount items"
                _isDeleteCountdownVisible.value = false
                _activeDeleteCandidates.value = emptyList()
                _activeDetailsFile.value = null
                clearSelection()
            }
        }

        viewModelScope.launch {
            val isAuthEnabled = prefsRepo.isBiometricAuthEnabled.first()
            if (!isAuthEnabled) {
                performDelete()
            } else {
                if (activity == null) {
                    _userMessage.value = "Unable to start authentication"
                    return@launch
                }
                val title = if (candidates.size == 1) "Confirm Deletion" else "Confirm Bulk Deletion"
                val subtitle = if (candidates.size == 1) "Authenticate to delete ${candidates[0].name}" else "Authenticate to delete ${candidates.size} files"

                securityManager.authenticate(
                    activity = activity,
                    title = title,
                    subtitle = subtitle,
                    onSuccess = performDelete,
                    onError = { error ->
                        _userMessage.value = error
                    }
                )
            }
        }
    }
    fun moveToTrash(activity: FragmentActivity?, file: FileEntity) {
        val performMoveToTrash: () -> Unit = {
            viewModelScope.launch {
                val result = fileOps.moveToTrash(file.path)
                if (result.isSuccess) {
                    _userMessage.value = "Moved \"${file.name}\" to Trash"
                    _activeDetailsFile.value = null
                } else {
                    _userMessage.value = result.exceptionOrNull()?.message ?: "Failed to move to Trash"
                }
            }
        }

        viewModelScope.launch {
            val isAuthEnabled = prefsRepo.isBiometricAuthEnabled.first()
            if (!isAuthEnabled) {
                performMoveToTrash()
            } else {
                if (activity == null) {
                    _userMessage.value = "Unable to start authentication"
                    return@launch
                }
                securityManager.authenticate(
                    activity = activity,
                    title = "Confirm Move to Trash",
                    subtitle = "Authenticate to move ${file.name} to Trash",
                    onSuccess = performMoveToTrash,
                    onError = { error ->
                        _userMessage.value = error
                    }
                )
            }
        }
    }

    fun moveToTrashSelected(activity: FragmentActivity?) {
        val paths = _selectedPaths.value.toList()
        if (paths.isEmpty()) return

        val performBulkMoveToTrash: () -> Unit = {
            viewModelScope.launch {
                val results = fileOps.bulkMoveToTrash(paths)
                val successCount = results.count { it.value }
                _userMessage.value = "Moved $successCount items to Trash"
                clearSelection()
            }
        }

        viewModelScope.launch {
            val isAuthEnabled = prefsRepo.isBiometricAuthEnabled.first()
            if (!isAuthEnabled) {
                performBulkMoveToTrash()
            } else {
                if (activity == null) {
                    _userMessage.value = "Unable to start authentication"
                    return@launch
                }
                securityManager.authenticate(
                    activity = activity,
                    title = "Confirm Move to Trash",
                    subtitle = "Authenticate to move ${paths.size} files to Trash",
                    onSuccess = performBulkMoveToTrash,
                    onError = { error ->
                        _userMessage.value = error
                    }
                )
            }
        }
    }

    fun openFile(file: FileEntity) {
        fileOps.openFile(file.path)
    }

    fun shareFile(file: FileEntity) {
        fileOps.shareFile(file.path)
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }
}
