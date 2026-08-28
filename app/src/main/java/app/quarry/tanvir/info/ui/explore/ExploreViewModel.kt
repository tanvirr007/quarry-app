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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    DATE_ASC("Date: Oldest first")
}

private data class Tuple5<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
)

data class ExploreUiState(
    val currentPath: String = Environment.getExternalStorageDirectory().absolutePath,
    val viewMode: ExploreViewMode = ExploreViewMode.TREEMAP,
    val searchQuery: String = "",
    val selectedCategory: StorageCategory? = null,
    val sortOrder: FileSortOrder = FileSortOrder.SIZE_DESC,
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

    val uiState: StateFlow<ExploreUiState> = combine(
        combine(_currentPath, _viewMode, _searchQuery, _selectedCategory, _sortOrder) { path, mode, query, cat, sort ->
            ExploreUiState(
                currentPath = path,
                viewMode = mode,
                searchQuery = query,
                selectedCategory = cat,
                sortOrder = sort
            )
        },
        combine(_selectedPaths, _activeDetailsFile, _activeRenameFile, _activeDeleteCandidates, _isDeleteCountdownVisible) { selected, details, rename, deleteCandidates, deleteVisible ->
            Tuple5(selected, details, rename, deleteCandidates, deleteVisible)
        },
        combine(_userMessage, repository.getLargestFiles(100)) { msg, largest ->
            Pair(msg, largest)
        }
    ) { baseState, selectionAndDialogs, msgAndLargest ->
        baseState.copy(
            selectedPaths = selectionAndDialogs.first,
            isSelectionMode = selectionAndDialogs.first.isNotEmpty(),
            activeDetailsFile = selectionAndDialogs.second,
            activeRenameFile = selectionAndDialogs.third,
            activeDeleteCandidates = selectionAndDialogs.fourth,
            isDeleteCountdownVisible = selectionAndDialogs.fifth,
            userMessage = msgAndLargest.first,
            largestFiles = msgAndLargest.second
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ExploreUiState()
    )

    private val _directoryFiles = MutableStateFlow<List<FileEntity>>(emptyList())
    val directoryFiles: StateFlow<List<FileEntity>> = _directoryFiles.asStateFlow()

    private val _treemapLayoutNodes = MutableStateFlow<List<TreemapNode>>(emptyList())
    val treemapLayoutNodes: StateFlow<List<TreemapNode>> = _treemapLayoutNodes.asStateFlow()

    init {
        loadDirectory(defaultRootPath)
    }

    fun setViewMode(mode: ExploreViewMode) {
        _viewMode.value = mode
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
        loadDirectory(path)
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

    private fun loadDirectory(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val db = app.quarry.tanvir.info.data.database.QuarryDatabase.getInstance(getApplication())
            val children = db.fileDao().getChildrenSync(path)
            _directoryFiles.value = children

            // Compute Treemap layout for this folder
            val tree = TreemapEngine.buildTree(children, path)
            val layout = TreemapEngine.layoutSquarified(
                items = tree.children,
                bounds = TreemapRect(0f, 0f, 1000f, 1000f)
            )
            _treemapLayoutNodes.value = layout
        }
    }

    fun recalculateTreemap(widthPx: Float, heightPx: Float) {
        if (widthPx <= 0 || heightPx <= 0) return
        val currentChildren = _directoryFiles.value
        if (currentChildren.isEmpty()) return

        viewModelScope.launch(Dispatchers.Default) {
            val tree = TreemapEngine.buildTree(currentChildren, _currentPath.value)
            val layout = TreemapEngine.layoutSquarified(
                items = tree.children,
                bounds = TreemapRect(0f, 0f, widthPx, heightPx)
            )
            _treemapLayoutNodes.value = layout
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
        val all = _directoryFiles.value.map { it.path }.toSet()
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
        activity: FragmentActivity,
        file: FileEntity,
        newName: String
    ) {
        securityManager.authenticate(
            activity = activity,
            title = "Confirm File Rename",
            subtitle = "Authenticate to rename ${file.name}",
            onSuccess = {
                viewModelScope.launch {
                    val result = fileOps.renameFile(file.path, newName)
                    if (result.isSuccess) {
                        _userMessage.value = "Renamed to $newName"
                        _activeRenameFile.value = null
                        _activeDetailsFile.value = null
                        loadDirectory(_currentPath.value)
                    } else {
                        _userMessage.value = result.exceptionOrNull()?.message ?: "Rename failed"
                    }
                }
            },
            onError = { error ->
                _userMessage.value = error
            }
        )
    }

    fun promptDeleteSingle(file: FileEntity) {
        _activeDeleteCandidates.value = listOf(file)
        _isDeleteCountdownVisible.value = true
    }

    fun promptDeleteSelected() {
        val paths = _selectedPaths.value
        val files = _directoryFiles.value.filter { paths.contains(it.path) }
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
        activity: FragmentActivity,
        candidates: List<FileEntity>
    ) {
        val title = if (candidates.size == 1) "Confirm Deletion" else "Confirm Bulk Deletion"
        val subtitle = if (candidates.size == 1) "Authenticate to delete ${candidates[0].name}" else "Authenticate to delete ${candidates.size} files"

        securityManager.authenticate(
            activity = activity,
            title = title,
            subtitle = subtitle,
            onSuccess = {
                viewModelScope.launch {
                    val paths = candidates.map { it.path }
                    val results = fileOps.bulkDelete(paths)
                    val successCount = results.count { it.value }
                    _userMessage.value = "Deleted $successCount items"
                    _isDeleteCountdownVisible.value = false
                    _activeDeleteCandidates.value = emptyList()
                    _activeDetailsFile.value = null
                    clearSelection()
                    loadDirectory(_currentPath.value)
                }
            },
            onError = { error ->
                _userMessage.value = error
            }
        )
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
