package com.wise.file_manager

import android.app.Application
import android.content.Context
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.*
import com.wise.file_manager.db.FileMetadata

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.*

@Immutable
enum class AppScreen {
    Home, Explorer, PdfViewer, AudioPlayer, ImageViewer, VideoPlayer, TextViewer, ArchiveExplorer
}

@Immutable
enum class FilterMode {
    None, Images, Videos, Audio, Apks, Archives, Documents, FilesOnly
}

@Immutable
enum class SortType {
    Name, Size, Date, Type
}

@Immutable
enum class SortOrder {
    Ascending, Descending
}

@Immutable
enum class ViewMode {
    Compact, Detailed, Columned, Grid, Gallery, Minimal
}

@Immutable
enum class CreateType {
    Folder, File, Database
}

@Immutable
data class NavigationItem(
    val title: String,
    val path: String,
    val icon: String,
    val filterMode: FilterMode = FilterMode.None
)

@Immutable
data class SearchOptions(
    val searchSubfolders: Boolean = false,
    val caseSensitive: Boolean = false,
    val regex: Boolean = false
)

@Immutable
data class ExplorerTab(
    val id: String = UUID.randomUUID().toString(),
    val path: String,
    val pathStack: List<String> = emptyList(),
    val files: List<File> = emptyList(),
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0
)

class FileViewModel(application: Application) : AndroidViewModel(application) {

    private val rootDirectory = Environment.getExternalStorageDirectory()
    private val prefs = application.getSharedPreferences("pdf_viewer_prefs", Context.MODE_PRIVATE)
    private val repository = FileRepository(application)
    
    private val _currentScreen = MutableStateFlow(AppScreen.Home)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    fun navigateToScreen(screen: AppScreen) {
        _currentScreen.value = screen
    }

    // Tab Management
    private val _tabs = MutableStateFlow<List<ExplorerTab>>(listOf(ExplorerTab(path = rootDirectory.absolutePath)))
    val tabs: StateFlow<List<ExplorerTab>> = _tabs.asStateFlow()

    private val _activeTabIndex = MutableStateFlow(0)
    val activeTabIndex: StateFlow<Int> = _activeTabIndex.asStateFlow()

    fun addNewTab(path: String? = null) {
        val currentTab = _tabs.value.getOrNull(_activeTabIndex.value) ?: _tabs.value.first()
        val newPath = path ?: currentTab.path
        val newList = _tabs.value.toMutableList()
        
        // Clone current files if path matches
        val initialFiles = if (newPath == currentTab.path) currentTab.files else fileCache[newPath] ?: emptyList()
        
        val newTab = ExplorerTab(
            path = newPath,
            files = initialFiles,
            pathStack = currentTab.pathStack
        )
        newList.add(newTab)
        _tabs.value = newList
        val newIndex = newList.size - 1
        _activeTabIndex.value = newIndex
        
        _currentPath.value = newPath
        _files.value = initialFiles
        updateBreadcrumbs(newPath)
    }

    fun switchTab(index: Int) {
        if (index in _tabs.value.indices) {
            _activeTabIndex.value = index
            val tab = _tabs.value[index]
            _currentPath.value = tab.path
            _files.value = tab.files
            updateBreadcrumbs(tab.path)
        }
    }

    fun closeTab(index: Int) {
        val newList = _tabs.value.toMutableList()
        if (newList.size > 1 && index in newList.indices) {
            newList.removeAt(index)
            _tabs.value = newList
            val nextIndex = if (_activeTabIndex.value >= newList.size) newList.size - 1 else _activeTabIndex.value
            _activeTabIndex.value = nextIndex
            val nextTab = newList[nextIndex]
            _currentPath.value = nextTab.path
            _files.value = nextTab.files
            updateBreadcrumbs(nextTab.path)
        }
    }

    fun moveTab(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in _tabs.value.indices || toIndex !in _tabs.value.indices || fromIndex == toIndex) return
        
        val newList = _tabs.value.toMutableList()
        val tab = newList.removeAt(fromIndex)
        newList.add(toIndex, tab)
        _tabs.value = newList
        
        // Update active index if it was affected
        if (_activeTabIndex.value == fromIndex) {
            _activeTabIndex.value = toIndex
        } else if (_activeTabIndex.value in (minOf(fromIndex, toIndex)..maxOf(fromIndex, toIndex))) {
            if (fromIndex < toIndex) {
                _activeTabIndex.value -= 1
            } else {
                _activeTabIndex.value += 1
            }
        }
    }

    fun updateScrollPosition(index: Int, offset: Int) {
        val activeIndex = _activeTabIndex.value
        val currentTabs = _tabs.value.toMutableList()
        if (activeIndex in currentTabs.indices) {
            currentTabs[activeIndex] = currentTabs[activeIndex].copy(
                scrollIndex = index,
                scrollOffset = offset
            )
            _tabs.value = currentTabs
        }
    }

    private val _currentPath = MutableStateFlow(rootDirectory.absolutePath)
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val _files = MutableStateFlow<List<File>>(emptyList())
    val files: StateFlow<List<File>> = _files.asStateFlow()

    private val _breadcrumbs = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val breadcrumbs: StateFlow<List<Pair<String, String>>> = _breadcrumbs.asStateFlow()

    private val _selectedFiles = MutableStateFlow<Set<File>>(emptySet())
    val selectedFiles: StateFlow<Set<File>> = _selectedFiles.asStateFlow()

    private val _previewFile = MutableStateFlow<File?>(null)
    val previewFile: StateFlow<File?> = _previewFile.asStateFlow()

    private val _showDeleteConfirmation = MutableStateFlow(false)
    val showDeleteConfirmation: StateFlow<Boolean> = _showDeleteConfirmation.asStateFlow()

    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog: StateFlow<Boolean> = _showCreateDialog.asStateFlow()

    private val _showInfoDialog = MutableStateFlow(false)
    val showInfoDialog: StateFlow<Boolean> = _showInfoDialog.asStateFlow()

    private val _infoFile = MutableStateFlow<File?>(null)
    val infoFile: StateFlow<File?> = _infoFile.asStateFlow()

    private val _infoSize = MutableStateFlow("Calculating...")
    val infoSize: StateFlow<String> = _infoSize.asStateFlow()

    private val _infoFileCount = MutableStateFlow(0)
    val infoFileCount: StateFlow<Int> = _infoFileCount.asStateFlow()

    private val _infoFolderCount = MutableStateFlow(0)
    val infoFolderCount: StateFlow<Int> = _infoFolderCount.asStateFlow()

    private var infoJob: Job? = null

    fun showInfo(file: File) {
        _infoFile.value = file
        _showInfoDialog.value = true
        _infoSize.value = if (file.isDirectory) "Calculating..." else FileUtils.formatFileSize(file.length())
        _infoFileCount.value = 0
        _infoFolderCount.value = 0
        
        if (file.isDirectory) {
            infoJob?.cancel()
            infoJob = viewModelScope.launch(Dispatchers.IO) {
                val stats = calculateFolderStats(file)
                withContext(Dispatchers.Main) {
                    _infoSize.value = FileUtils.formatFileSize(stats.size)
                    _infoFileCount.value = stats.fileCount
                    _infoFolderCount.value = stats.folderCount
                }
            }
        }
    }

    private data class FolderStats(val size: Long, val fileCount: Int, val folderCount: Int)

    private fun calculateFolderStats(file: File): FolderStats {
        var size = 0L
        var files = 0
        var folders = 0
        
        val children = file.listFiles() ?: return FolderStats(0, 0, 0)
        for (f in children) {
            if (f.isDirectory) {
                folders++
                val subStats = calculateFolderStats(f)
                size += subStats.size
                files += subStats.fileCount
                folders += subStats.folderCount
            } else {
                files++
                size += f.length()
            }
        }
        return FolderStats(size, files, folders)
    }

    fun hideInfo() {
        _showInfoDialog.value = false
        _infoFile.value = null
        infoJob?.cancel()
    }

    private val _createType = MutableStateFlow(CreateType.Folder)
    val createType: StateFlow<CreateType> = _createType.asStateFlow()

    fun showCreateDialog(type: CreateType? = null) {
        type?.let { _createType.value = it }
        _showCreateDialog.value = true
    }

    fun updateCreateType(type: CreateType) {
        _createType.value = type
    }

    fun hideCreateDialog() {
        _showCreateDialog.value = false
    }

    // Search State
    private val _isDiscoveryMode = MutableStateFlow(false)
    val isDiscoveryMode: StateFlow<Boolean> = _isDiscoveryMode.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<File>>(emptyList())
    val searchResults: StateFlow<List<File>> = _searchResults.asStateFlow()

    private val _searchOptions = MutableStateFlow(SearchOptions())
    val searchOptions: StateFlow<SearchOptions> = _searchOptions.asStateFlow()

    private val _currentFilter = MutableStateFlow(FilterMode.None)
    val currentFilter: StateFlow<FilterMode> = _currentFilter.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    // Caches
    private val fileCache = mutableMapOf<String, List<File>>()
    private val folderSizeCache = mutableMapOf<String, String>()

    // Sorting State
    private val _sortType = MutableStateFlow(SortType.Name)
    val sortType: StateFlow<SortType> = _sortType.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.Ascending)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _foldersFirst = MutableStateFlow(true)
    val foldersFirst: StateFlow<Boolean> = _foldersFirst.asStateFlow()

    private val _showSortOptions = MutableStateFlow(false)
    val showSortOptions: StateFlow<Boolean> = _showSortOptions.asStateFlow()

    // View State
    private val _viewMode = MutableStateFlow(ViewMode.Detailed)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    private val _showViewOptions = MutableStateFlow(false)
    val showViewOptions: StateFlow<Boolean> = _showViewOptions.asStateFlow()

    // PDF Viewer State
    private val _pdfHistory = MutableStateFlow<Map<String, Int>>(emptyMap())
    val pdfHistory: StateFlow<Map<String, Int>> = _pdfHistory.asStateFlow()

    private val _pdfLibrary = MutableStateFlow<List<File>>(emptyList())
    val pdfLibrary: StateFlow<List<File>> = _pdfLibrary.asStateFlow()

    private val _recentPdfs = MutableStateFlow<List<File>>(emptyList())
    val recentPdfs: StateFlow<List<File>> = _recentPdfs.asStateFlow()

    suspend fun getCachedFolderInfo(path: String) = repository.getCachedMetadata(path)

    fun getFolderSize(file: File): String {
        return folderSizeCache[file.absolutePath] ?: "..."
    }

    fun updateFolderSizeCache(path: String, size: String) {
        folderSizeCache[path] = size
    }

    fun updatePdfHistory(path: String, page: Int) {
        val current = _pdfHistory.value.toMutableMap()
        current[path] = page
        _pdfHistory.value = current
        saveHistory()
        
        // Update recent list
        val file = File(path)
        val currentRecent = _recentPdfs.value.filter { it.absolutePath != path }.toMutableList()
        currentRecent.add(0, file)
        _recentPdfs.value = currentRecent.take(18) // Keep last 18
        saveRecents()
    }

    private fun saveRecents() {
        val paths = _recentPdfs.value.map { it.absolutePath }
        prefs.edit().putString("recent_pdfs", JSONArray(paths).toString()).apply()
    }

    private fun loadRecents() {
        val json = prefs.getString("recent_pdfs", null) ?: return
        try {
            val arr = JSONArray(json)
            val files = mutableListOf<File>()
            for (i in 0 until arr.length()) {
                val path = arr.getString(i)
                val file = File(path)
                if (file.exists()) files.add(file)
            }
            _recentPdfs.value = files
        } catch (e: Exception) {}
    }

    private fun saveHistory() {
        val history = _pdfHistory.value
        val json = JSONObject()
        history.forEach { (path, page) -> json.put(path, page) }
        prefs.edit().putString("pdf_history", json.toString()).apply()
    }

    private fun loadHistory() {
        val json = prefs.getString("pdf_history", null) ?: return
        try {
            val obj = JSONObject(json)
            val history = mutableMapOf<String, Int>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                history[key] = obj.getInt(key)
            }
            _pdfHistory.value = history
        } catch (e: Exception) {}
    }

    fun loadPdfLibrary() {
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            val results = MediaStoreUtils.getFilesByCategory(getApplication(), FilterMode.Documents)
                .filter { it.extension.lowercase() in listOf("pdf", "epub") }
            
            withContext(Dispatchers.Main) {
                _pdfLibrary.value = results.sortedByDescending { it.lastModified() }
                _isProcessing.value = false
            }
        }
    }

    // Audio Player State
    private val _audioPlaylist = MutableStateFlow<List<File>>(emptyList())
    val audioPlaylist: StateFlow<List<File>> = _audioPlaylist.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    // Image Viewer State
    private val _imagePlaylist = MutableStateFlow<List<File>>(emptyList())
    val imagePlaylist: StateFlow<List<File>> = _imagePlaylist.asStateFlow()

    // Video Player State
    private val _videoPlaylist = MutableStateFlow<List<File>>(emptyList())
    val videoPlaylist: StateFlow<List<File>> = _videoPlaylist.asStateFlow()

    // Discovery State
    private var discoveryJob: Job? = null
    private var filterJob: Job? = null

    val storageLocations = listOf(
        NavigationItem("Internal Storage", rootDirectory.absolutePath, "storage"),
        NavigationItem("Root", "/", "root")
    )

    val categories = listOf(
        NavigationItem("All Files", "", "all_files", FilterMode.FilesOnly),
        NavigationItem("Images", "", "image", FilterMode.Images),
        NavigationItem("Videos", "", "movie", FilterMode.Videos),
        NavigationItem("Audio", "", "music", FilterMode.Audio),
        NavigationItem("APKs", "", "apk", FilterMode.Apks),
        NavigationItem("Archives", "", "archive", FilterMode.Archives),
        NavigationItem("Documents", "", "description", FilterMode.Documents),
        NavigationItem("Downloads", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath, "download"),
        NavigationItem("DCIM", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath, "camera")
    )

    init {
        loadRecents()
        loadHistory()
        loadFiles(rootDirectory.absolutePath)
        
        // Background Full Indexing on startup
        viewModelScope.launch(Dispatchers.IO) {
            repository.fullIndex()
        }
    }

    fun applyFilter(mode: FilterMode) {
        _currentFilter.value = mode
        if (!_isDiscoveryMode.value) {
            _currentPath.value = rootDirectory.absolutePath
        }
        performDiscovery()
    }

    fun enterDiscoveryMode() {
        _isDiscoveryMode.value = true
        performDiscovery()
    }

    fun exitDiscoveryMode() {
        _isDiscoveryMode.value = false
        _searchQuery.value = ""
        _currentFilter.value = FilterMode.None
        discoveryJob?.cancel()
        _searchResults.value = emptyList()
        loadFiles(_currentPath.value)
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        performDiscovery()
    }

    fun updateSearchOptions(options: SearchOptions) {
        _searchOptions.value = options
        performDiscovery()
    }

    fun updatePreviewFile(file: File?) {
        _previewFile.value = file
    }

    // Sorting Logic
    fun showSortOptions() { _showSortOptions.value = true }
    fun hideSortOptions() { _showSortOptions.value = false }

    fun updateSort(type: SortType? = null, order: SortOrder? = null, foldersFirst: Boolean? = null) {
        type?.let { _sortType.value = it }
        order?.let { _sortOrder.value = it }
        foldersFirst?.let { _foldersFirst.value = it }
        refresh()
    }

    // View Logic
    fun showViewOptions() { _showViewOptions.value = true }
    fun hideViewOptions() { _showViewOptions.value = false }

    fun updateViewMode(mode: ViewMode) {
        _viewMode.value = mode
        hideViewOptions()
    }

    private fun getFileComparator(): Comparator<File> {
        val baseComparator = when (_sortType.value) {
            SortType.Name -> compareBy<File> { it.name.lowercase() }
            SortType.Size -> compareBy<File> { it.length() }
            SortType.Date -> compareBy<File> { it.lastModified() }
            SortType.Type -> compareBy<File> { it.extension.lowercase() }
        }

        val orderedComparator = if (_sortOrder.value == SortOrder.Descending) baseComparator.reversed() else baseComparator

        return if (_foldersFirst.value) {
            compareBy<File> { !it.isDirectory }.thenComparing(orderedComparator)
        } else {
            orderedComparator
        }
    }

    private fun performDiscovery() {
        discoveryJob?.cancel()
        val query = _searchQuery.value
        val filter = _currentFilter.value
        val options = _searchOptions.value

        if (query.isEmpty() && filter == FilterMode.None) {
            _searchResults.value = emptyList()
            _isProcessing.value = false
            return
        }

        _searchResults.value = emptyList()
        _isProcessing.value = true

        discoveryJob = viewModelScope.launch(Dispatchers.IO) {
            if (query.isNotEmpty()) delay(200)
            
            val isGlobalFilter = !_isDiscoveryMode.value && filter != FilterMode.None
            
            if (isGlobalFilter && query.isEmpty()) {
                val results = MediaStoreUtils.getFilesByCategory(getApplication(), filter)
                val finalSorted = results.sortedWith(getFileComparator())
                withContext(Dispatchers.Main) {
                    _searchResults.value = finalSorted
                    _isProcessing.value = false
                }
                return@launch
            }

            if (isGlobalFilter && query.isNotEmpty()) {
                val dbResults = repository.search(query)
                if (dbResults.isNotEmpty()) {
                    val filteredResults = dbResults.filter { file ->
                        if (filter == FilterMode.None) true else {
                            val ext = file.extension.lowercase()
                            when (filter) {
                                FilterMode.Images -> FileUtils.isImage(ext)
                                FilterMode.Videos -> FileUtils.isVideo(ext)
                                FilterMode.Audio -> FileUtils.isAudio(ext)
                                FilterMode.Apks -> FileUtils.isApk(ext)
                                FilterMode.Archives -> FileUtils.isArchive(ext)
                                FilterMode.Documents -> FileUtils.isPdf(ext) || FileUtils.isDocx(ext) || FileUtils.isText(ext)
                                else -> true
                            }
                        }
                    }
                    val finalSorted = filteredResults.sortedWith(getFileComparator())
                    withContext(Dispatchers.Main) {
                        _searchResults.value = finalSorted
                        _isProcessing.value = false
                    }
                    return@launch
                }
            }

            val results = mutableListOf<File>()
            var lastUpdateTime = System.currentTimeMillis()
            val searchPath = if (isGlobalFilter) rootDirectory.absolutePath else _currentPath.value
            val root = File(searchPath)

            val walkRoot = if (options.searchSubfolders || isGlobalFilter) {
                root.walk().maxDepth(if (isGlobalFilter) 8 else Int.MAX_VALUE)
            } else {
                (root.listFiles() ?: emptyArray()).asSequence()
            }

            walkRoot.filter { file ->
                val matchesQuery = if (query.isEmpty()) true else matchFile(file, query, options)
                if (file.isDirectory) {
                    val isFileTypeFilterActive = filter != FilterMode.None && filter != FilterMode.FilesOnly
                    matchesQuery && !isFileTypeFilterActive && filter != FilterMode.FilesOnly
                } else {
                    val matchesFilter = if (filter == FilterMode.None) true else {
                        val ext = file.extension.lowercase()
                        when (filter) {
                            FilterMode.Images -> FileUtils.isImage(ext)
                            FilterMode.Videos -> FileUtils.isVideo(ext)
                            FilterMode.Audio -> FileUtils.isAudio(ext)
                            FilterMode.Apks -> FileUtils.isApk(ext)
                            FilterMode.Archives -> FileUtils.isArchive(ext)
                            FilterMode.Documents -> FileUtils.isPdf(ext) || FileUtils.isDocx(ext) || FileUtils.isText(ext)
                            FilterMode.FilesOnly -> true
                            else -> false
                        }
                    }
                    matchesQuery && matchesFilter
                }
            }.forEach { foundFile ->
                results.add(foundFile)
                val now = System.currentTimeMillis()
                if (now - lastUpdateTime > 250) { // Increased buffer interval
                    val snapshot = results.toList()
                    withContext(Dispatchers.Main) { _searchResults.value = snapshot }
                    lastUpdateTime = now
                }
            }

            val finalSorted = results.toList().sortedWith(getFileComparator())
            withContext(Dispatchers.Main) {
                _searchResults.value = finalSorted
                _isProcessing.value = false
            }
        }
    }

    fun openDirectory(file: File) {
        if (file.isDirectory) {
            if (_isDiscoveryMode.value) exitDiscoveryMode()
            _currentFilter.value = FilterMode.None
            
            val activeIndex = _activeTabIndex.value
            val currentTabs = _tabs.value.toMutableList()
            val activeTab = currentTabs[activeIndex]
            currentTabs[activeIndex] = activeTab.copy(
                pathStack = activeTab.pathStack + activeTab.path
            )
            _tabs.value = currentTabs
            
            loadFiles(file.absolutePath)
            clearSelection()
        } else {
            val extension = file.extension.lowercase()
            if (FileUtils.isPdf(extension)) {
                _previewFile.value = file
                _currentScreen.value = AppScreen.PdfViewer
            } else if (FileUtils.isAudio(extension)) {
                val currentFiles = if (_isDiscoveryMode.value || _currentFilter.value != FilterMode.None) _searchResults.value else _files.value
                _audioPlaylist.value = currentFiles.filter { FileUtils.isAudio(it.extension.lowercase()) }
                _previewFile.value = file
                _currentScreen.value = AppScreen.AudioPlayer
            } else if (FileUtils.isImage(extension)) {
                val currentFiles = if (_isDiscoveryMode.value || _currentFilter.value != FilterMode.None) _searchResults.value else _files.value
                _imagePlaylist.value = currentFiles.filter { FileUtils.isImage(it.extension.lowercase()) }
                _previewFile.value = file
                _currentScreen.value = AppScreen.ImageViewer
            } else if (FileUtils.isVideo(extension)) {
                val currentFiles = if (_isDiscoveryMode.value || _currentFilter.value != FilterMode.None) _searchResults.value else _files.value
                _videoPlaylist.value = currentFiles.filter { FileUtils.isVideo(it.extension.lowercase()) }
                _previewFile.value = file
                _currentScreen.value = AppScreen.VideoPlayer
            } else if (FileUtils.isArchive(extension)) {
                _previewFile.value = file
                _currentScreen.value = AppScreen.ArchiveExplorer
            } else if (FileUtils.isText(extension)) {
                _previewFile.value = file
                _currentScreen.value = AppScreen.TextViewer
            } else if (FileUtils.isLikelyText(file)) {
                // Double check it's not a known binary type before opening as text
                if (!FileUtils.isPdf(extension) && !FileUtils.isArchive(extension)) {
                    _previewFile.value = file
                    _currentScreen.value = AppScreen.TextViewer
                }
            } else {
                // For completely unknown files, do not open in any viewer automatically
                _previewFile.value = null 
                // Potentially trigger a system "Open With" here in the future
            }
        }
    }

    fun playNextImage() {
        val playlist = _imagePlaylist.value
        val currentFile = _previewFile.value
        if (playlist.isNotEmpty() && currentFile != null) {
            val currentIndex = playlist.indexOf(currentFile)
            if (currentIndex != -1 && currentIndex < playlist.size - 1) {
                _previewFile.value = playlist[currentIndex + 1]
            } else if (currentIndex == playlist.size - 1) {
                _previewFile.value = playlist[0] // Loop to start
            }
        }
    }

    fun playPreviousImage() {
        val playlist = _imagePlaylist.value
        val currentFile = _previewFile.value
        if (playlist.isNotEmpty() && currentFile != null) {
            val currentIndex = playlist.indexOf(currentFile)
            if (currentIndex != -1 && currentIndex > 0) {
                _previewFile.value = playlist[currentIndex - 1]
            } else if (currentIndex == 0) {
                _previewFile.value = playlist.last() // Loop to end
            }
        }
    }

    fun playNextAudio() {
        val playlist = _audioPlaylist.value
        val currentFile = _previewFile.value
        if (playlist.isNotEmpty() && currentFile != null) {
            val currentIndex = playlist.indexOf(currentFile)
            if (currentIndex != -1 && currentIndex < playlist.size - 1) {
                _previewFile.value = playlist[currentIndex + 1]
            } else if (currentIndex == playlist.size - 1) {
                _previewFile.value = playlist[0] // Loop to start
            }
        }
    }

    fun playPreviousAudio() {
        val playlist = _audioPlaylist.value
        val currentFile = _previewFile.value
        if (playlist.isNotEmpty() && currentFile != null) {
            val currentIndex = playlist.indexOf(currentFile)
            if (currentIndex != -1 && currentIndex > 0) {
                _previewFile.value = playlist[currentIndex - 1]
            } else if (currentIndex == 0) {
                _previewFile.value = playlist.last() // Loop to end
            }
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
    }

    fun closePreview() {
        _previewFile.value = null
        if (_currentScreen.value == AppScreen.PdfViewer || _currentScreen.value == AppScreen.AudioPlayer || 
            _currentScreen.value == AppScreen.ImageViewer || _currentScreen.value == AppScreen.VideoPlayer) {
            _currentScreen.value = AppScreen.Explorer
        }
    }

    fun navigateToPath(path: String) {
        if (path != _currentPath.value || _currentFilter.value != FilterMode.None) {
            if (_isDiscoveryMode.value) exitDiscoveryMode()
            _currentFilter.value = FilterMode.None
            
            val activeIndex = _activeTabIndex.value
            val currentTabs = _tabs.value.toMutableList()
            val activeTab = currentTabs[activeIndex]
            currentTabs[activeIndex] = activeTab.copy(
                pathStack = activeTab.pathStack + activeTab.path
            )
            _tabs.value = currentTabs
            
            loadFiles(path)
            clearSelection()
        }
    }

    fun refresh() {
        val path = _currentPath.value
        fileCache.remove(path)
        folderSizeCache.clear()

        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            // Force refresh from disk and re-index
            repository.getFilesForPath(path, forceRefresh = true)

            withContext(Dispatchers.Main) {
                _isProcessing.value = false
                if (_isDiscoveryMode.value || _currentFilter.value != FilterMode.None) {
                    performDiscovery()
                } else {
                    loadFiles(path)
                }
            }
        }
    }

    fun createItem(name: String, type: CreateType) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentDir = File(_currentPath.value)
                if (!currentDir.exists() || !currentDir.isDirectory) return@launch

                val newFile = when (type) {
                    CreateType.Folder -> File(currentDir, name).apply { mkdirs() }
                    CreateType.File -> File(currentDir, name).apply { createNewFile() }
                    CreateType.Database -> File(currentDir, if (name.endsWith(".db")) name else "$name.db").apply { createNewFile() }
                }

                withContext(Dispatchers.Main) {
                    refresh()
                }
            } catch (e: Exception) {
                // Error handling could be added here
            }
        }
    }
    fun goBack(): Boolean {
        if (_currentScreen.value == AppScreen.PdfViewer || _currentScreen.value == AppScreen.AudioPlayer || 
            _currentScreen.value == AppScreen.ImageViewer || _currentScreen.value == AppScreen.VideoPlayer) {
            _currentScreen.value = AppScreen.Explorer
            _previewFile.value = null
            return true
        }
        if (_isDiscoveryMode.value) {
            exitDiscoveryMode()
            return true
        }
        if (_currentFilter.value != FilterMode.None) {
            _currentFilter.value = FilterMode.None
            loadFiles(_currentPath.value)
            return true
        }
        
        val activeIndex = _activeTabIndex.value
        val currentTabs = _tabs.value.toMutableList()
        val activeTab = currentTabs[activeIndex]
        if (activeTab.pathStack.isNotEmpty()) {
            val previousPath = activeTab.pathStack.last()
            currentTabs[activeIndex] = activeTab.copy(
                pathStack = activeTab.pathStack.dropLast(1)
            )
            _tabs.value = currentTabs
            loadFiles(previousPath)
            clearSelection()
            return true
        }
        return false
    }

    fun toggleSelection(file: File) {
        val current = _selectedFiles.value.toMutableSet()
        if (current.contains(file)) {
            current.remove(file)
        } else {
            current.add(file)
        }
        _selectedFiles.value = current
    }

    fun selectAll() {
        val listToSelect = if (_isDiscoveryMode.value || _currentFilter.value != FilterMode.None) _searchResults.value else _files.value
        if (_selectedFiles.value.size == listToSelect.size && listToSelect.isNotEmpty()) {
            clearSelection()
        } else {
            _selectedFiles.value = listToSelect.toSet()
        }
    }

    fun clearSelection() {
        _selectedFiles.value = emptySet()
    }

    fun showDeleteConfirmation() {
        if (_selectedFiles.value.isNotEmpty()) {
            _showDeleteConfirmation.value = true
        }
    }

    fun hideDeleteConfirmation() {
        _showDeleteConfirmation.value = false
    }

    fun deleteSelected() {
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            _selectedFiles.value.forEach { file ->
                deleteRecursively(file)
            }
            withContext(Dispatchers.Main) {
                clearSelection()
                hideDeleteConfirmation()
                _isProcessing.value = false
                refresh()
            }
        }
    }

    private fun matchFile(file: File, query: String, options: SearchOptions): Boolean {
        return if (options.caseSensitive) {
            file.name.contains(query)
        } else {
            file.name.lowercase().contains(query.lowercase())
        }
    }
    
    private fun deleteRecursively(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursively(it) }
        }
        file.delete()
    }

    private fun loadFiles(path: String, fromTabSwitch: Boolean = false) {
        _currentPath.value = path
        updateBreadcrumbs(path)
        
        // Update active tab path if not switching
        if (!fromTabSwitch) {
            val newList = _tabs.value.toMutableList()
            if (_activeTabIndex.value in newList.indices) {
                newList[_activeTabIndex.value] = newList[_activeTabIndex.value].copy(path = path)
                _tabs.value = newList
            }
        }
        
        discoveryJob?.cancel()
        filterJob?.cancel()

        val cachedFiles = fileCache[path]
        if (cachedFiles != null) {
            _files.value = cachedFiles
            _isProcessing.value = false
            return
        }

        _isProcessing.value = true
        viewModelScope.launch(Dispatchers.IO) {
            // Priority: FileRepository (Cached in DB)
            val list = repository.getFilesForPath(path, forceRefresh = false)
            val sortedList = list.sortedWith(getFileComparator())

            fileCache[path] = sortedList

            withContext(Dispatchers.Main) {
                _files.value = sortedList
                val newList = _tabs.value.toMutableList()
                if (_activeTabIndex.value in newList.indices) {
                    newList[_activeTabIndex.value] = newList[_activeTabIndex.value].copy(files = sortedList)
                    _tabs.value = newList
                }
                _isProcessing.value = false
            }
        }
    }

    private fun updateBreadcrumbs(path: String) {
        val externalStorageRoot = Environment.getExternalStorageDirectory().absolutePath
        val breadcrumbList = mutableListOf<Pair<String, String>>()
        
        if (path.startsWith(externalStorageRoot)) {
            breadcrumbList.add("Internal Storage" to externalStorageRoot)
            val subPath = path.removePrefix(externalStorageRoot).trim('/')
            if (subPath.isNotEmpty()) {
                val subParts = subPath.split("/")
                var tempPath = externalStorageRoot
                subParts.forEach { part ->
                    tempPath = "$tempPath/$part"
                    breadcrumbList.add(part to tempPath)
                }
            }
        } else {
            val parts = path.split("/").filter { it.isNotEmpty() }
            var currentAccumulatedPath = ""
            parts.forEach { part ->
                currentAccumulatedPath += "/$part"
                breadcrumbList.add(part to currentAccumulatedPath)
            }
        }
        _breadcrumbs.value = breadcrumbList
    }
}
