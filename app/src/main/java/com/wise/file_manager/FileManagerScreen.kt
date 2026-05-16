package com.wise.file_manager

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileManagerScreen(viewModel: FileViewModel = viewModel()) {
    val currentPath by viewModel.currentPath.collectAsState()
    val files by viewModel.files.collectAsState()
    val breadcrumbs by viewModel.breadcrumbs.collectAsState()
    val selectedFiles by viewModel.selectedFiles.collectAsState()
    val previewFile by viewModel.previewFile.collectAsState()
    val showDeleteConfirmation by viewModel.showDeleteConfirmation.collectAsState()
    
    val isDiscoveryMode by viewModel.isDiscoveryMode.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val searchOptions by viewModel.searchOptions.collectAsState()
    val currentFilter by viewModel.currentFilter.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    
    val showSortOptions by viewModel.showSortOptions.collectAsState()
    val currentSortType by viewModel.sortType.collectAsState()
    val currentSortOrder by viewModel.sortOrder.collectAsState()
    val foldersFirst by viewModel.foldersFirst.collectAsState()

    val showViewOptions by viewModel.showViewOptions.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    previewFile?.let { file ->
        val extension = file.extension.lowercase()
        if (!FileUtils.isPdf(extension) && !FileUtils.isAudio(extension) && 
            !FileUtils.isImage(extension) && !FileUtils.isVideo(extension)) {
            FilePreviewDialog(file = file, onDismiss = { viewModel.closePreview() })
        }
    }

    if (showSortOptions) {
        SortBottomSheet(
            currentType = currentSortType,
            currentOrder = currentSortOrder,
            foldersFirst = foldersFirst,
            onDismiss = { viewModel.hideSortOptions() },
            onSortChange = { type, order, fFirst -> viewModel.updateSort(type, order, fFirst) }
        )
    }

    if (showViewOptions) {
        ViewBottomSheet(
            currentMode = viewMode,
            onDismiss = { viewModel.hideViewOptions() },
            onModeChange = { viewModel.updateViewMode(it) }
        )
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.hideDeleteConfirmation() },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Files?") },
            text = { Text("Are you sure you want to delete ${selectedFiles.size} selected items? This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteSelected() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideDeleteConfirmation() }) { Text("Cancel") }
            }
        )
    }

    BackHandler {
        if (selectedFiles.isNotEmpty()) viewModel.clearSelection()
        else if (isDiscoveryMode) viewModel.exitDiscoveryMode()
        else if (drawerState.isOpen) scope.launch { drawerState.close() }
        else viewModel.goBack()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(screenWidth / 2),
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                drawerTonalElevation = 0.dp
            ) {
                Spacer(Modifier.height(12.dp))
                Text("MiXplorer Clone", modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                NavigationSectionLabel("Storage")
                viewModel.storageLocations.forEach { item ->
                    DrawerItem(item, currentPath.startsWith(item.path)) {
                        viewModel.navigateToPath(item.path); scope.launch { drawerState.close() }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 28.dp))
                NavigationSectionLabel("Quick Access")
                viewModel.categories.forEach { item ->
                    val isFilterSelected = item.filterMode != FilterMode.None && currentFilter == item.filterMode
                    val isPathSelected = item.path.isNotEmpty() && currentPath == item.path
                    
                    DrawerItem(item, isFilterSelected || isPathSelected) {
                        if (item.filterMode != FilterMode.None) viewModel.applyFilter(item.filterMode)
                        else viewModel.navigateToPath(item.path)
                        scope.launch { drawerState.close() }
                    }
                }
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Surface(tonalElevation = 2.dp) {
                    Column {
                        if (selectedFiles.isNotEmpty()) {
                            TopAppBar(
                                title = { Text("${selectedFiles.size}", style = MaterialTheme.typography.titleMedium) },
                                navigationIcon = { IconButton(onClick = { viewModel.clearSelection() }) { Icon(Icons.Default.Close, contentDescription = "Close") } },
                                actions = {
                                    TooltipIconButton("Copy", Icons.Default.ContentCopy) { scope.launch { snackbarHostState.showSnackbar("Copied") } }
                                    TooltipIconButton("Move", Icons.Default.ContentCut) { scope.launch { snackbarHostState.showSnackbar("Cut") } }
                                    TooltipIconButton("Delete", Icons.Default.Delete) { viewModel.showDeleteConfirmation() }
                                    TooltipIconButton("Rename", Icons.Default.Edit) { scope.launch { snackbarHostState.showSnackbar("Coming soon") } }
                                    TooltipIconButton("Archive", Icons.Default.Archive) { scope.launch { snackbarHostState.showSnackbar("Coming soon") } }
                                    TooltipIconButton("Select All", Icons.Default.SelectAll) { viewModel.selectAll() }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                            )
                        } else {
                            TopAppBar(
                                title = { 
                                    LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                                        items(breadcrumbs) { breadcrumb ->
                                            Text(text = breadcrumb.first, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { viewModel.navigateToPath(breadcrumb.second) }.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.titleSmall, color = if (breadcrumb.second == currentPath) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                            if (breadcrumb != breadcrumbs.last()) Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                                        }
                                    }
                                },
                                navigationIcon = { IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, contentDescription = "Menu") } },
                                actions = {
                                    IconButton(onClick = { if (isDiscoveryMode) viewModel.exitDiscoveryMode() else viewModel.enterDiscoveryMode() }) { Icon(if (isDiscoveryMode) Icons.Default.Close else Icons.Default.Search, contentDescription = "Search & Filter") }
                                    IconButton(onClick = { /* More */ }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                                }
                            )
                        }
                        if (isProcessing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            bottomBar = {
                if (!isDiscoveryMode || selectedFiles.isNotEmpty()) {
                    BottomAppBar(containerColor = MaterialTheme.colorScheme.surfaceContainer, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            TooltipIconButton("Search & Filter", Icons.Default.Search) { viewModel.enterDiscoveryMode() }
                            TooltipIconButton("New", Icons.Default.Add) { scope.launch { snackbarHostState.showSnackbar("Coming soon") } }
                            TooltipIconButton("Refresh", Icons.Default.Refresh) { viewModel.refresh(); scope.launch { snackbarHostState.showSnackbar("Refreshed") } }
                            TooltipIconButton("Select All", Icons.Default.SelectAll) { viewModel.selectAll() }
                            TooltipIconButton("Sort", Icons.Default.SortByAlpha) { viewModel.showSortOptions() }
                            TooltipIconButton("View", Icons.Default.GridView) { viewModel.showViewOptions() }
                            TooltipIconButton("More", Icons.Default.MoreVert) { }
                        }
                    }
                }
            }
        ) { paddingValues ->
            val isSearching = isDiscoveryMode || currentFilter != FilterMode.None
            val hasInput = searchQuery.isNotEmpty() || currentFilter != FilterMode.None
            val displayFiles = if (isSearching && hasInput) searchResults else files

            Box(modifier = Modifier.fillMaxSize()) {
                // Adaptive File List based on ViewMode
                if (viewMode == ViewMode.Grid || viewMode == ViewMode.Gallery) {
                    val columns = if (viewMode == ViewMode.Gallery) 4 else 3
                    val spacing = if (viewMode == ViewMode.Gallery) 0.dp else 4.dp
                    
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = paddingValues.calculateTopPadding() + (if (viewMode == ViewMode.Gallery) 0.dp else 8.dp),
                            bottom = if (isDiscoveryMode) 100.dp else paddingValues.calculateBottomPadding() + (if (viewMode == ViewMode.Gallery) 0.dp else 8.dp),
                            start = if (viewMode == ViewMode.Gallery) 0.dp else 8.dp,
                            end = if (viewMode == ViewMode.Gallery) 0.dp else 8.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(spacing),
                        verticalArrangement = Arrangement.spacedBy(spacing)
                    ) {
                        items(displayFiles, key = { it.absolutePath }) { file ->
                            if (viewMode == ViewMode.Gallery) {
                                FileItemGallery(file, selectedFiles.contains(file),
                                    onClick = { if (selectedFiles.isNotEmpty()) viewModel.toggleSelection(file) else viewModel.openDirectory(file) },
                                    onLongClick = { viewModel.toggleSelection(file) })
                            } else {
                                FileItemGrid(file, selectedFiles.contains(file), viewMode, 
                                    onClick = { if (selectedFiles.isNotEmpty()) viewModel.toggleSelection(file) else viewModel.openDirectory(file) },
                                    onLongClick = { viewModel.toggleSelection(file) })
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = paddingValues.calculateTopPadding(),
                            bottom = if (isDiscoveryMode) 100.dp else paddingValues.calculateBottomPadding(),
                            start = 0.dp,
                            end = 0.dp
                        )
                    ) {
                        items(displayFiles, key = { it.absolutePath }) { file ->
                            FileItemExpressive(file, selectedFiles.contains(file), viewMode,
                                onClick = { if (selectedFiles.isNotEmpty()) viewModel.toggleSelection(file) else viewModel.openDirectory(file) },
                                onLongClick = { viewModel.toggleSelection(file) })
                        }
                    }
                }

                if (displayFiles.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(if (isSearching && hasInput) Icons.Default.SearchOff else Icons.Default.FolderOpen, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(Modifier.height(16.dp))
                            Text(if (isSearching && hasInput) "No results found" else "Empty Folder", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Keyboard-attached Search
                AnimatedVisibility(visible = isDiscoveryMode && selectedFiles.isEmpty(), enter = slideInVertically(initialOffsetY = { it }) + fadeIn(), exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
                    Surface(tonalElevation = 8.dp, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding(), color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
                        SearchSubBar(query = searchQuery, options = searchOptions, currentFilter = currentFilter, onQueryChange = { viewModel.updateSearchQuery(it) }, onFilterChange = { viewModel.applyFilter(it) }, onOptionsChange = { viewModel.updateSearchOptions(it) }, onExit = { viewModel.exitDiscoveryMode() })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileItemGallery(file: File, isSelected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        ThumbnailView(
            file = file, 
            isSelected = isSelected, 
            size = 128.dp, // Increased size for larger icons and seamless look
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun FileItemGrid(file: File, isSelected: Boolean, viewMode: ViewMode, onClick: () -> Unit, onLongClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().aspectRatio(0.9f),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
        ),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                ThumbnailView(file, isSelected, size = if (viewMode == ViewMode.Gallery) 100.dp else 72.dp)
            }
            Text(file.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
fun FileItemExpressive(file: File, isSelected: Boolean, viewMode: ViewMode, onClick: () -> Unit, onLongClick: () -> Unit) {
    val verticalPadding = when(viewMode) {
        ViewMode.Compact -> 4.dp
        ViewMode.Minimal -> 2.dp
        else -> 10.dp
    }

    val folderInfo = if (file.isDirectory && (viewMode == ViewMode.Detailed || viewMode == ViewMode.Columned)) {
        produceState(initialValue = "...", file) {
            value = withContext(Dispatchers.IO) {
                try {
                    val allItems = file.listFiles()
                    val files = allItems?.count { it.isFile } ?: 0
                    val folders = allItems?.count { it.isDirectory } ?: 0
                    if (viewMode == ViewMode.Columned) "$files F • $folders D" else "$files files"
                } catch (e: Exception) { "Unknown" }
            }
        }.value
    } else ""
    
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp),
        shape = RoundedCornerShape(if (viewMode == ViewMode.Minimal) 8.dp else 16.dp),
        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = verticalPadding), 
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (viewMode != ViewMode.Minimal) {
                ThumbnailView(file, isSelected, size = if (viewMode == ViewMode.Compact) 32.dp else 48.dp)
                Spacer(modifier = Modifier.width(16.dp))
            }
            
            // Column 1: Name
            Column(modifier = Modifier.weight(if (viewMode == ViewMode.Columned) 0.5f else 1f)) {
                Text(file.name, style = if (viewMode == ViewMode.Compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge, fontWeight = if (file.isDirectory) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (viewMode == ViewMode.Detailed) {
                    val date = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(file.lastModified()))
                    val details = if (file.isDirectory) folderInfo else FileUtils.formatFileSize(file.length())
                    Text("$details • $date", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (viewMode == ViewMode.Columned) {
                // Column 2: Date
                Text(
                    text = SimpleDateFormat("MMM dd, yy", Locale.getDefault()).format(Date(file.lastModified())),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.25f),
                    textAlign = TextAlign.End
                )
                // Column 3: Size/Count
                Text(
                    text = if (file.isDirectory) folderInfo else FileUtils.formatFileSize(file.length()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(0.25f),
                    textAlign = TextAlign.End,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun ThumbnailView(file: File, isSelected: Boolean, size: androidx.compose.ui.unit.Dp = 48.dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val extension = file.extension.lowercase()
    Box(
        contentAlignment = Alignment.Center, 
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(if (size > 80.dp) 0.dp else size/4)) // Seamless for gallery (large size), rounded for list/grid
            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
    ) {
        when {
            file.isDirectory -> Icon(Icons.Default.Folder, null, tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary, modifier = Modifier.size(size * 0.6f))
            FileUtils.isImage(extension) || FileUtils.isVideo(extension) -> {
                val imageLoader = remember { ImageLoader.Builder(context).components { add(VideoFrameDecoder.Factory()) }.build() }
                AsyncImage(model = ImageRequest.Builder(context).data(file).crossfade(true).build(), imageLoader = imageLoader, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, error = androidx.compose.ui.graphics.painter.ColorPainter(Color.Transparent))
                if (FileUtils.isVideo(extension) && !isSelected) Icon(Icons.Default.PlayCircle, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(size * 0.4f).align(Alignment.Center))
            }
            FileUtils.isAudio(extension) -> Icon(Icons.Default.MusicNote, null, tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color(0xFF9C27B0), modifier = Modifier.size(size * 0.5f))
            FileUtils.isPdf(extension) -> PdfThumbnail(file, size)
            FileUtils.isApk(extension) -> Icon(Icons.Default.Android, null, tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color(0xFF4CAF50), modifier = Modifier.size(size * 0.5f))
            FileUtils.isArchive(extension) -> Icon(Icons.Default.Inventory2, null, tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color(0xFFFF9800), modifier = Modifier.size(size * 0.5f))
            FileUtils.isDocx(extension) -> Icon(Icons.Default.Description, null, tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color(0xFF2B579A), modifier = Modifier.size(size * 0.5f))
            FileUtils.isExcel(extension) || FileUtils.isCsv(extension) -> Icon(Icons.Default.TableChart, null, tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color(0xFF217346), modifier = Modifier.size(size * 0.5f))
            FileUtils.isDb(extension) -> Icon(Icons.Default.Storage, null, tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color(0xFF607D8B), modifier = Modifier.size(size * 0.5f))
            FileUtils.isText(extension) -> Icon(Icons.AutoMirrored.Filled.Article, null, tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.Gray, modifier = Modifier.size(size * 0.5f))
            else -> Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary, modifier = Modifier.size(size * 0.5f))
        }
        if (isSelected) Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(size * 0.5f)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewBottomSheet(currentMode: ViewMode, onDismiss: () -> Unit, onModeChange: (ViewMode) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text("View Mode", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 16.dp))
            val modes = listOf(
                Triple("Detailed", ViewMode.Detailed, Icons.Default.ViewList),
                Triple("Compact", ViewMode.Compact, Icons.Default.FormatListBulleted),
                Triple("Grid", ViewMode.Grid, Icons.Default.GridView),
                Triple("Gallery", ViewMode.Gallery, Icons.Default.Collections),
                Triple("Columned", ViewMode.Columned, Icons.Default.ViewColumn),
                Triple("Minimal", ViewMode.Minimal, Icons.Default.List)
            )
            modes.forEach { (label, mode, icon) ->
                NavigationDrawerItem(
                    label = { Text(label) },
                    selected = currentMode == mode,
                    onClick = { onModeChange(mode) },
                    icon = { Icon(icon, null) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun SearchSubBar(query: String, options: SearchOptions, currentFilter: FilterMode, onQueryChange: (String) -> Unit, onFilterChange: (FilterMode) -> Unit, onOptionsChange: (SearchOptions) -> Unit, onExit: () -> Unit) {
    var showOptions by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(value = query, onValueChange = onQueryChange, modifier = Modifier.weight(1f).heightIn(min = 48.dp).focusRequester(focusRequester), placeholder = { Text("Filter files...", style = MaterialTheme.typography.bodyMedium) }, leadingIcon = { IconButton(onClick = onExit) { Icon(Icons.Default.Close, null, modifier = Modifier.size(20.dp)) } }, trailingIcon = { if (query.isNotEmpty() || currentFilter != FilterMode.None) IconButton(onClick = { onQueryChange(""); onFilterChange(FilterMode.None) }) { Icon(Icons.Default.Clear, null, modifier = Modifier.size(20.dp)) } }, colors = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent), shape = RoundedCornerShape(24.dp), singleLine = true, textStyle = MaterialTheme.typography.bodyMedium)
            IconButton(onClick = { showOptions = !showOptions }) { Icon(Icons.Default.Tune, null, tint = if (showOptions) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp)) }
        }
        AnimatedVisibility(visible = showOptions) {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    item { FilterChip(selected = options.searchSubfolders, onClick = { onOptionsChange(options.copy(searchSubfolders = !options.searchSubfolders)) }, label = { Text("Subfolders", fontSize = 11.sp) }, leadingIcon = if (options.searchSubfolders) { { Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp)) } } else null) }
                    item { FilterChip(selected = options.caseSensitive, onClick = { onOptionsChange(options.copy(caseSensitive = !options.caseSensitive)) }, label = { Text("Case Sensitive", fontSize = 11.sp) }, leadingIcon = if (options.caseSensitive) { { Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp)) } } else null) }
                    val filters = listOf("Images" to FilterMode.Images, "Videos" to FilterMode.Videos, "Audio" to FilterMode.Audio, "APKs" to FilterMode.Apks, "Archives" to FilterMode.Archives, "Docs" to FilterMode.Documents)
                    items(filters) { (label, mode) -> FilterChip(selected = currentFilter == mode, onClick = { onFilterChange(if (currentFilter == mode) FilterMode.None else mode) }, label = { Text(label, fontSize = 11.sp) }) }
                }
            }
        }
        HorizontalDivider()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortBottomSheet(currentType: SortType, currentOrder: SortOrder, foldersFirst: Boolean, onDismiss: () -> Unit, onSortChange: (SortType?, SortOrder?, Boolean?) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text("Sort Options", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 16.dp))
            Text("Sort By", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val types = listOf("Name" to SortType.Name, "Size" to SortType.Size, "Date" to SortType.Date, "Type" to SortType.Type)
                types.forEach { (label, type) -> FilterChip(selected = currentType == type, onClick = { onSortChange(type, null, null) }, label = { Text(label) }) }
            }
            Spacer(Modifier.height(16.dp))
            Text("Order", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = currentOrder == SortOrder.Ascending, onClick = { onSortChange(null, SortOrder.Ascending, null) }, label = { Text("Ascending") }, leadingIcon = { Icon(Icons.Default.ArrowUpward, null, modifier = Modifier.size(16.dp)) })
                FilterChip(selected = currentOrder == SortOrder.Descending, onClick = { onSortChange(null, SortOrder.Descending, null) }, label = { Text("Descending") }, leadingIcon = { Icon(Icons.Default.ArrowDownward, null, modifier = Modifier.size(16.dp)) })
            }
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Folders First", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = foldersFirst, onCheckedChange = { onSortChange(null, null, it) })
            }
        }
    }
}

fun getIconForType(type: String): ImageVector {
    return when (type) {
        "storage" -> Icons.Default.SdStorage
        "root" -> Icons.Default.SettingsSuggest
        "download" -> Icons.Default.Download
        "camera" -> Icons.Default.PhotoCamera
        "image" -> Icons.Default.Image
        "movie" -> Icons.Default.Movie
        "music" -> Icons.Default.MusicNote
        "description" -> Icons.Default.Description
        "apk" -> Icons.Default.Android
        "archive" -> Icons.Default.Inventory2
        "all_files" -> Icons.AutoMirrored.Filled.List
        else -> Icons.Default.Folder
    }
}

@Composable
fun NavigationSectionLabel(label: String) {
    Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
}

@Composable
fun DrawerItem(item: NavigationItem, isSelected: Boolean, onClick: () -> Unit) {
    NavigationDrawerItem(label = { Text(item.title, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) }, selected = isSelected, onClick = onClick, icon = { Icon(getIconForType(item.icon), null, tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp), shape = RoundedCornerShape(16.dp), colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f), unselectedContainerColor = Color.Transparent))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Surface(onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(0.dp), color: Color = MaterialTheme.colorScheme.surface, tonalElevation: androidx.compose.ui.unit.Dp = 0.dp, content: @Composable () -> Unit) {
    Box(modifier = modifier.clip(shape).background(color).combinedClickable(onClick = onClick, onLongClick = onLongClick)) { content() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TooltipIconButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(), tooltip = { PlainTooltip { Text(label) } }, state = rememberTooltipState()) { IconButton(onClick = onClick) { Icon(icon, contentDescription = label) } }
}
