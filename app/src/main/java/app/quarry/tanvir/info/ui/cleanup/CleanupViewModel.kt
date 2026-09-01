package app.quarry.tanvir.info.ui.cleanup

import android.app.Application
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.quarry.tanvir.info.data.database.FileEntity
import app.quarry.tanvir.info.data.preferences.UserPreferencesRepository
import app.quarry.tanvir.info.domain.cleanup.CleanupCandidateGroup
import app.quarry.tanvir.info.domain.cleanup.DefaultCleanupEngine
import app.quarry.tanvir.info.domain.duplicates.DuplicateGroup
import app.quarry.tanvir.info.domain.duplicates.FastDuplicateDetector
import app.quarry.tanvir.info.domain.file.FileOperationsManager
import app.quarry.tanvir.info.domain.file.TrashItem
import app.quarry.tanvir.info.domain.file.TrashManager
import app.quarry.tanvir.info.domain.model.StorageItem
import app.quarry.tanvir.info.domain.scanner.ExclusionMatcher
import app.quarry.tanvir.info.domain.scanner.ScanProgress
import app.quarry.tanvir.info.domain.scanner.ScanRepository
import app.quarry.tanvir.info.domain.scanner.ScanState
import app.quarry.tanvir.info.domain.security.BiometricSecurityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed interface DuplicateScanState {
    data object Idle : DuplicateScanState
    data class Scanning(val message: String) : DuplicateScanState
    data class Completed(val groups: List<DuplicateGroup>) : DuplicateScanState
    data class Error(val message: String) : DuplicateScanState
}

data class CleanupUiState(
    val duplicateScanState: DuplicateScanState = DuplicateScanState.Idle,
    val duplicateGroups: List<DuplicateGroup> = emptyList(),
    val candidateGroups: List<CleanupCandidateGroup> = emptyList(),
    val totalRecoverableBytes: Long = 0L,
    val trashItems: List<TrashItem> = emptyList(),
    val isBiometricEnabled: Boolean = true,
    val isStorageScanning: Boolean = false,
    val storageScanProgress: ScanProgress? = null,
    val activeCandidateGroup: CleanupCandidateGroup? = null,
    val selectedItemPaths: Set<String> = emptySet(),
    val activeDeleteItems: List<StorageItem> = emptyList(),
    val isDeleteCountdownVisible: Boolean = false,
    val isTrashDialogVisible: Boolean = false,
    val userMessage: String? = null
)

class CleanupViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ScanRepository.getInstance(application)
    private val prefsRepo = UserPreferencesRepository.getInstance(application)
    private val duplicateDetector = FastDuplicateDetector()
    private val cleanupEngine = DefaultCleanupEngine(duplicateDetector)
    private val trashManager = TrashManager.getInstance(application, repository)
    private val fileOps = FileOperationsManager(application, repository)
    private val securityManager = BiometricSecurityManager(application)

    private val _duplicateScanState = MutableStateFlow<DuplicateScanState>(DuplicateScanState.Idle)
    private val _duplicateGroups = MutableStateFlow<List<DuplicateGroup>>(emptyList())
    private val _candidateGroups = MutableStateFlow<List<CleanupCandidateGroup>>(emptyList())
    private val _activeCandidateGroup = MutableStateFlow<CleanupCandidateGroup?>(null)
    private val _selectedItemPaths = MutableStateFlow<Set<String>>(emptySet())
    private val _activeDeleteItems = MutableStateFlow<List<StorageItem>>(emptyList())
    private val _isDeleteCountdownVisible = MutableStateFlow(false)
    private val _isTrashDialogVisible = MutableStateFlow(false)
    private val _userMessage = MutableStateFlow<String?>(null)

    private data class DialogState(
        val activeGroup: CleanupCandidateGroup?,
        val selectedPaths: Set<String>,
        val deleteItems: List<StorageItem>,
        val isDeleteVisible: Boolean,
        val isTrashVisible: Boolean
    )

    val uiState: StateFlow<CleanupUiState> = combine(
        combine(_duplicateScanState, _duplicateGroups, _candidateGroups, trashManager.trashItems, prefsRepo.isBiometricAuthEnabled, repository.scanState) { args ->
            val dupState = args[0] as DuplicateScanState
            @Suppress("UNCHECKED_CAST")
            val dupGroups = args[1] as List<DuplicateGroup>
            @Suppress("UNCHECKED_CAST")
            val candGroups = args[2] as List<CleanupCandidateGroup>
            @Suppress("UNCHECKED_CAST")
            val trash = args[3] as List<TrashItem>
            val biometric = args[4] as Boolean
            val repoScanState = args[5] as ScanState

            val trashRecoverable = trash.sumOf { it.size }
            val dupRecoverable = dupGroups.sumOf { it.recoverableBytes }
            val candRecoverable = candGroups.flatMap { it.items }.distinctBy { it.path }.sumOf { it.size }
            val totalRecoverable = trashRecoverable + dupRecoverable + candRecoverable

            CleanupUiState(
                duplicateScanState = dupState,
                duplicateGroups = dupGroups,
                candidateGroups = candGroups,
                totalRecoverableBytes = totalRecoverable,
                trashItems = trash,
                isBiometricEnabled = biometric,
                isStorageScanning = repoScanState is ScanState.Scanning,
                storageScanProgress = (repoScanState as? ScanState.Scanning)?.progress
            )
        },
        combine(_activeCandidateGroup, _selectedItemPaths, _activeDeleteItems, _isDeleteCountdownVisible, _isTrashDialogVisible) { group, paths, deleteItems, deleteVis, trashVis ->
            DialogState(group, paths, deleteItems, deleteVis, trashVis)
        },
        _userMessage
    ) { baseState, dialogState, userMsg ->
        baseState.copy(
            activeCandidateGroup = dialogState.activeGroup,
            selectedItemPaths = dialogState.selectedPaths,
            activeDeleteItems = dialogState.deleteItems,
            isDeleteCountdownVisible = dialogState.isDeleteVisible,
            isTrashDialogVisible = dialogState.isTrashVisible,
            userMessage = userMsg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CleanupUiState()
    )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            combine(
                repository.getAllFiles(),
                prefsRepo.excludedFolders
            ) { allFiles, excluded ->
                val filtered = if (excluded.isEmpty()) allFiles else allFiles.filter { entity ->
                    !ExclusionMatcher.isExcluded(entity.path, excluded) &&
                            !ExclusionMatcher.isExcluded(entity.parentPath ?: "", excluded)
                }
                cleanupEngine.getCandidatesFromEntities(filtered)
            }.collect { candidates ->
                _candidateGroups.value = candidates
            }
        }
    }

    fun loadCandidates() {
        viewModelScope.launch(Dispatchers.IO) {
            val allFiles = repository.getAllFilesSync()
            val excluded = prefsRepo.excludedFolders.first()
            val filtered = if (excluded.isEmpty()) allFiles else allFiles.filter { entity ->
                !ExclusionMatcher.isExcluded(entity.path, excluded) &&
                        !ExclusionMatcher.isExcluded(entity.parentPath ?: "", excluded)
            }
            val candidates = cleanupEngine.getCandidatesFromEntities(filtered)
            _candidateGroups.value = candidates
        }
    }

    fun rescanStorage() {
        if (repository.scanState.value !is ScanState.Scanning) {
            repository.startScan()
        }
    }

    fun scanForDuplicates(forceStorageRescan: Boolean = false) {
        if (_duplicateScanState.value is DuplicateScanState.Scanning) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (forceStorageRescan) {
                    _duplicateScanState.value = DuplicateScanState.Scanning("Scanning storage…")
                    if (repository.scanState.value !is ScanState.Scanning) {
                        repository.startScan()
                    }

                    // Await transition into Scanning (up to 2 seconds)
                    try {
                        withTimeoutOrNull(2000L) {
                            repository.scanState.first { it is ScanState.Scanning }
                        }
                    } catch (_: Exception) {}

                    // Await completion of storage scan
                    val finishState = repository.scanState.first { it !is ScanState.Scanning }
                    if (finishState is ScanState.Cancelled || finishState is ScanState.Error) {
                        _duplicateScanState.value = DuplicateScanState.Error("Storage scan interrupted")
                        return@launch
                    }
                }

                _duplicateScanState.value = DuplicateScanState.Scanning("Clustering identical file sizes…")
                val db = app.quarry.tanvir.info.data.database.QuarryDatabase.getInstance(getApplication())
                val rawCandidates = db.fileDao().getPotentialDuplicateSizeCandidates()
                val excluded = prefsRepo.excludedFolders.first()
                val potentialCandidates = if (excluded.isEmpty()) rawCandidates else rawCandidates.filter { entity ->
                    !ExclusionMatcher.isExcluded(entity.path, excluded) &&
                            !ExclusionMatcher.isExcluded(entity.parentPath ?: "", excluded)
                }

                if (potentialCandidates.isEmpty()) {
                    _duplicateScanState.value = DuplicateScanState.Completed(emptyList())
                    _duplicateGroups.value = emptyList()
                    return@launch
                }

                _duplicateScanState.value = DuplicateScanState.Scanning("Computing partial hashes…")
                val confirmed = duplicateDetector.findDuplicatesFromEntities(potentialCandidates)

                _duplicateGroups.value = confirmed
                _duplicateScanState.value = DuplicateScanState.Completed(confirmed)
            } catch (e: Exception) {
                _duplicateScanState.value = DuplicateScanState.Error(e.localizedMessage ?: "Duplicate scan failed")
            }
        }
    }

    fun openCandidateGroup(group: CleanupCandidateGroup) {
        _activeCandidateGroup.value = group
        _selectedItemPaths.value = emptySet()
    }

    fun closeCandidateGroup() {
        _activeCandidateGroup.value = null
        _selectedItemPaths.value = emptySet()
    }

    fun toggleItemSelection(path: String) {
        val current = _selectedItemPaths.value.toMutableSet()
        if (current.contains(path)) {
            current.remove(path)
        } else {
            current.add(path)
        }
        _selectedItemPaths.value = current
    }

    fun selectAllCandidates() {
        val group = _activeCandidateGroup.value ?: return
        val allPaths = group.items.map { it.path }.toSet()
        _selectedItemPaths.value = allPaths
    }

    fun deselectAllCandidates() {
        _selectedItemPaths.value = emptySet()
    }

    fun promptDeleteSelectedCandidates() {
        val group = _activeCandidateGroup.value ?: return
        val selectedPaths = _selectedItemPaths.value
        val itemsToDelete = group.items.filter { selectedPaths.contains(it.path) }
        if (itemsToDelete.isNotEmpty()) {
            _activeDeleteItems.value = itemsToDelete
            _isDeleteCountdownVisible.value = true
        }
    }

    fun promptDeleteSingleItem(item: StorageItem) {
        _activeDeleteItems.value = listOf(item)
        _isDeleteCountdownVisible.value = true
    }

    fun dismissDeleteDialog() {
        _isDeleteCountdownVisible.value = false
        _activeDeleteItems.value = emptyList()
    }

    fun moveSelectedCandidatesToTrash(activity: FragmentActivity?) {
        val group = _activeCandidateGroup.value ?: return
        val selectedPaths = _selectedItemPaths.value
        val itemsToTrash = group.items.filter { selectedPaths.contains(it.path) }
        if (itemsToTrash.isEmpty()) return

        val performMoveToTrash: () -> Unit = {
            viewModelScope.launch {
                val paths = itemsToTrash.map { it.path }
                val results = fileOps.bulkMoveToTrash(paths)
                val successCount = results.count { it.value }
                val trashedPaths = results.filter { it.value }.keys
                _userMessage.value = "Moved $successCount items to Trash"
                _selectedItemPaths.value = emptySet()
                _activeCandidateGroup.update { grp ->
                    if (grp == null) null
                    else {
                        val remaining = grp.items.filterNot { trashedPaths.contains(it.path) }
                        if (remaining.isEmpty()) null
                        else grp.copy(items = remaining, totalBytes = remaining.sumOf { it.size })
                    }
                }
                loadCandidates()
                if (_duplicateScanState.value is DuplicateScanState.Completed) {
                    scanForDuplicates()
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
                    subtitle = "Authenticate to move ${itemsToTrash.size} files to Trash",
                    onSuccess = performMoveToTrash,
                    onError = { error ->
                        _userMessage.value = error
                    }
                )
            }
        }
    }

    fun executeAuthenticatedDelete(
        activity: FragmentActivity?,
        items: List<StorageItem>
    ) {
        val performDelete: () -> Unit = {
            viewModelScope.launch {
                val paths = items.map { it.path }
                val results = fileOps.bulkDelete(paths)
                val count = results.count { it.value }
                val deletedPaths = results.filter { it.value }.keys
                _userMessage.value = "Cleaned up $count files"
                _isDeleteCountdownVisible.value = false
                _activeDeleteItems.value = emptyList()
                _selectedItemPaths.value = emptySet()
                _activeCandidateGroup.update { grp ->
                    if (grp == null) null
                    else {
                        val remaining = grp.items.filterNot { deletedPaths.contains(it.path) }
                        if (remaining.isEmpty()) null
                        else grp.copy(items = remaining, totalBytes = remaining.sumOf { it.size })
                    }
                }
                loadCandidates()
                if (_duplicateScanState.value is DuplicateScanState.Completed) {
                    scanForDuplicates()
                }
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
                val title = if (items.size == 1) "Confirm Deletion" else "Confirm Cleanup Deletion"
                val subtitle = "Authenticate to delete ${items.size} files (${app.quarry.tanvir.info.domain.model.StorageFormatter.formatBytes(items.sumOf { it.size })})"

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

    fun openTrashDialog() {
        _isTrashDialogVisible.value = true
    }

    fun closeTrashDialog() {
        _isTrashDialogVisible.value = false
    }

    fun restoreTrashItem(trashId: String) {
        viewModelScope.launch {
            val result = trashManager.restoreItem(trashId)
            if (result.isSuccess) {
                _userMessage.value = "Restored file"
                loadCandidates()
            } else {
                _userMessage.value = result.exceptionOrNull()?.message ?: "Restore failed"
            }
        }
    }

    fun restoreSelectedTrashItems(trashIds: List<String>) {
        if (trashIds.isEmpty()) return
        viewModelScope.launch {
            val results = trashManager.restoreItemsBatch(trashIds)
            val count = results.count { it.value }
            _userMessage.value = "Restored $count files"
            loadCandidates()
        }
    }

    fun deleteTrashItemForever(
        activity: FragmentActivity?,
        trashId: String
    ) {
        val performDelete: () -> Unit = {
            viewModelScope.launch {
                val result = trashManager.deletePermanently(trashId)
                if (result.isSuccess) {
                    _userMessage.value = "Deleted forever"
                } else {
                    _userMessage.value = "Failed to delete"
                }
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
                securityManager.authenticate(
                    activity = activity,
                    title = "Confirm Permanent Delete",
                    subtitle = "Authenticate to permanently remove this file",
                    onSuccess = performDelete,
                    onError = { error ->
                        _userMessage.value = error
                    }
                )
            }
        }
    }

    fun deleteSelectedTrashItemsForever(
        activity: FragmentActivity?,
        trashIds: List<String>
    ) {
        if (trashIds.isEmpty()) return
        val performDelete: () -> Unit = {
            viewModelScope.launch {
                val results = trashManager.deletePermanentlyBatch(trashIds)
                val count = results.count { it.value }
                _userMessage.value = "Permanently deleted $count files"
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
                securityManager.authenticate(
                    activity = activity,
                    title = "Confirm Permanent Delete",
                    subtitle = "Authenticate to permanently remove ${trashIds.size} files",
                    onSuccess = performDelete,
                    onError = { error ->
                        _userMessage.value = error
                    }
                )
            }
        }
    }

    fun emptyTrash(activity: FragmentActivity?) {
        val performEmpty: () -> Unit = {
            viewModelScope.launch {
                val result = trashManager.emptyTrash()
                if (result.isSuccess) {
                    _userMessage.value = "Emptied trash (${result.getOrDefault(0)} items)"
                }
            }
        }

        viewModelScope.launch {
            val isAuthEnabled = prefsRepo.isBiometricAuthEnabled.first()
            if (!isAuthEnabled) {
                performEmpty()
            } else {
                if (activity == null) {
                    _userMessage.value = "Unable to start authentication"
                    return@launch
                }
                securityManager.authenticate(
                    activity = activity,
                    title = "Empty Trash",
                    subtitle = "Authenticate to permanently delete all items in Trash",
                    onSuccess = performEmpty,
                    onError = { error ->
                        _userMessage.value = error
                    }
                )
            }
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }
}
