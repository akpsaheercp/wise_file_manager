package com.wise.file_manager

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@Composable
fun CreateTypeItem(type: CreateType, isSelected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
            .padding(8.dp)
    ) {
        Icon(
            icon, 
            contentDescription = type.name, 
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(32.dp)
        )
        Text(
            type.name, 
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileManagerScreen(viewModel: FileViewModel = viewModel()) {
    val currentPath by viewModel.currentPath.collectAsState()
    val files by viewModel.files.collectAsState()
    val breadcrumbs by viewModel.breadcrumbs.collectAsState()
    val selectedFiles by viewModel.selectedFiles.collectAsState()
    val previewFile by viewModel.previewFile.collectAsState()
    val showDeleteConfirmation by viewModel.showDeleteConfirmation.collectAsState()
    
    val tabs by viewModel.tabs.collectAsState()
    val activeTabIndex by viewModel.activeTabIndex.collectAsState()

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

    val showCreateDialog by viewModel.showCreateDialog.collectAsState()
    val showInfoDialog by viewModel.showInfoDialog.collectAsState()
    val infoFile by viewModel.infoFile.collectAsState()
    val infoSize by viewModel.infoSize.collectAsState()
    val infoFileCount by viewModel.infoFileCount.collectAsState()
    val infoFolderCount by viewModel.infoFolderCount.collectAsState()
    val createType by viewModel.createType.collectAsState()
    var showMoreMenu by remember { mutableStateOf(false) }
    var showCreateMenu by remember { mutableStateOf(false) }
    
    var isEditingPath by remember { mutableStateOf(false) }
    var pathEditValue by remember { mutableStateOf("") }
    val pathFocusRequester = remember { FocusRequester() }
    
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp

    // System Bar Controller
    val view = LocalView.current
    val window = (view.context as Activity).window
    val insetsController = remember { WindowCompat.getInsetsController(window, view) }

    // Scroll States
    val lazyGridState = rememberLazyGridState()
    val lazyListState = rememberLazyListState()

    // Persist scroll position to ViewModel
    LaunchedEffect(lazyGridState.firstVisibleItemIndex, lazyGridState.firstVisibleItemScrollOffset) {
        if (viewMode == ViewMode.Grid || viewMode == ViewMode.Gallery) {
            viewModel.updateScrollPosition(lazyGridState.firstVisibleItemIndex, lazyGridState.firstVisibleItemScrollOffset)
        }
    }
    LaunchedEffect(lazyListState.firstVisibleItemIndex, lazyListState.firstVisibleItemScrollOffset) {
        if (viewMode != ViewMode.Grid && viewMode != ViewMode.Gallery) {
            viewModel.updateScrollPosition(lazyListState.firstVisibleItemIndex, lazyListState.firstVisibleItemScrollOffset)
        }
    }

    // Restore scroll position when tab changes or returning from preview
    LaunchedEffect(activeTabIndex) {
        val tab = tabs.getOrNull(activeTabIndex)
        if (tab != null) {
            if (viewMode == ViewMode.Grid || viewMode == ViewMode.Gallery) {
                lazyGridState.scrollToItem(tab.scrollIndex, tab.scrollOffset)
            } else {
                lazyListState.scrollToItem(tab.scrollIndex, tab.scrollOffset)
            }
        }
    }

    val pagerState = rememberPagerState(initialPage = activeTabIndex) { tabs.size }

    LaunchedEffect(activeTabIndex) {
        if (pagerState.currentPage != activeTabIndex && activeTabIndex in 0 until tabs.size) {
            pagerState.animateScrollToPage(page = activeTabIndex)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != activeTabIndex) {
            viewModel.switchTab(pagerState.currentPage)
        }
    }

    // Scroll-linked Visibility (Locked to 1f)
    val barVisibility = 1f

    // Ensure System Bars are always visible
    LaunchedEffect(Unit) {
        insetsController.show(WindowInsetsCompat.Type.statusBars())
    }

    val isAtTop by remember {
        derivedStateOf {
            if (viewMode == ViewMode.Grid || viewMode == ViewMode.Gallery) {
                lazyGridState.firstVisibleItemIndex == 0 && lazyGridState.firstVisibleItemScrollOffset == 0
            } else {
                lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0
            }
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {}
    }

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

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        val focusRequester = remember { FocusRequester() }
        
        AlertDialog(
            onDismissRequest = { viewModel.hideCreateDialog() },
            title = { 
                Text("Create New", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) 
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Type Selection Chips/Icons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        CreateTypeItem(
                            type = CreateType.Folder,
                            isSelected = createType == CreateType.Folder,
                            icon = Icons.Default.Folder,
                            onClick = { viewModel.updateCreateType(CreateType.Folder) }
                        )
                        CreateTypeItem(
                            type = CreateType.File,
                            isSelected = createType == CreateType.File,
                            icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                            onClick = { viewModel.updateCreateType(CreateType.File) }
                        )
                        CreateTypeItem(
                            type = CreateType.Database,
                            isSelected = createType == CreateType.Database,
                            icon = Icons.Default.Storage,
                            onClick = { viewModel.updateCreateType(CreateType.Database) }
                        )
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotEmpty()) {
                            viewModel.createItem(name, createType)
                            viewModel.hideCreateDialog()
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideCreateDialog() }) { Text("Cancel") }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }

    if (showInfoDialog && infoFile != null) {
        val file = infoFile!!
        val date = SimpleDateFormat("MMMM dd, yyyy HH:mm:ss", Locale.getDefault()).format(Date(file.lastModified()))
        
        AlertDialog(
            onDismissRequest = { viewModel.hideInfo() },
            title = { Text("File Information", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoRow("Name", file.name)
                    InfoRow("Path", file.absolutePath)
                    InfoRow("Size", infoSize)
                    if (file.isDirectory) {
                        InfoRow("Contents", "$infoFileCount files, $infoFolderCount folders")
                    }
                    InfoRow("Modified", date)
                    InfoRow("Type", if (file.isDirectory) "Folder" else file.extension.uppercase().ifEmpty { "File" })
                    InfoRow("Permissions", "${if (file.canRead()) "R" else "-"}${if (file.canWrite()) "W" else "-"}${if (file.canExecute()) "X" else "-"}")
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.hideInfo() }) {
                    Text("Close")
                }
            }
        )
    }

    BackHandler {
        if (selectedFiles.isNotEmpty()) viewModel.clearSelection()
        else if (isDiscoveryMode) viewModel.exitDiscoveryMode()
        else if (drawerState.isOpen) scope.launch { drawerState.close() }
        else if (!viewModel.goBack()) viewModel.navigateToScreen(AppScreen.Home)
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
                
                NavigationSectionLabel("General")
                NavigationDrawerItem(
                    label = { Text("Home", fontWeight = if (viewModel.currentScreen.value == AppScreen.Home) FontWeight.Bold else FontWeight.Normal) },
                    selected = viewModel.currentScreen.value == AppScreen.Home,
                    onClick = { 
                        viewModel.navigateToScreen(AppScreen.Home)
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Default.Home, null) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(16.dp)
                )

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
            modifier = Modifier.nestedScroll(nestedScrollConnection),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 2.dp,
                    modifier = Modifier.graphicsLayer {
                        alpha = barVisibility
                        translationY = (1f - barVisibility) * -100f
                    }
                ) {
                    Column(modifier = Modifier.statusBarsPadding()) {
                        Box {
                            Column {
                                TopAppBar(
                                    title = { 
                                        if (isEditingPath) {
                                            BasicTextField(
                                                value = pathEditValue,
                                                onValueChange = { pathEditValue = it },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .focusRequester(pathFocusRequester),
                                                textStyle = MaterialTheme.typography.titleSmall.copy(color = MaterialTheme.colorScheme.primary),
                                                singleLine = true,
                                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                                    imeAction = androidx.compose.ui.text.input.ImeAction.Done
                                                ),
                                                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                                    onDone = {
                                                        viewModel.navigateToPath(pathEditValue)
                                                        isEditingPath = false
                                                    }
                                                )
                                            )
                                            LaunchedEffect(Unit) { pathFocusRequester.requestFocus() }
                                        } else {
                                            LazyRow(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { 
                                                        pathEditValue = currentPath
                                                        isEditingPath = true 
                                                    }, 
                                                horizontalArrangement = Arrangement.spacedBy(2.dp), 
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                items(breadcrumbs) { breadcrumb ->
                                                    val displayName = if (breadcrumb.first == "0") "Device Storage" else breadcrumb.first
                                                    Text(text = displayName, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { viewModel.navigateToPath(breadcrumb.second) }.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.titleSmall, color = if (breadcrumb.second == currentPath) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                                    if (breadcrumb != breadcrumbs.last()) Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                                                }
                                            }
                                        }
                                    },
                                    navigationIcon = { 
                                        if (isEditingPath) {
                                            IconButton(onClick = { isEditingPath = false }) { Icon(Icons.Default.Close, contentDescription = "Cancel") }
                                        } else {
                                            IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, contentDescription = "Menu") } 
                                        }
                                    },
                                    actions = {
                                        if (isEditingPath) {
                                            IconButton(onClick = { 
                                                viewModel.navigateToPath(pathEditValue)
                                                isEditingPath = false 
                                            }) { Icon(Icons.Default.Check, contentDescription = "Navigate") }
                                        } else {
                                            IconButton(onClick = { if (isDiscoveryMode) viewModel.exitDiscoveryMode() else viewModel.enterDiscoveryMode() }) { Icon(if (isDiscoveryMode) Icons.Default.Close else Icons.Default.Search, contentDescription = "Search & Filter") }
                                            IconButton(onClick = { /* More */ }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                                        }
                                    },
                                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                                )
                                if (tabs.size > 1) {
                                    ExplorerTabBar(
                                        tabs = tabs,
                                        pagerState = pagerState,
                                        onTabClick = { viewModel.switchTab(it) },
                                        onTabClose = { viewModel.closeTab(it) },
                                        onMoveTab = { from, to -> viewModel.moveTab(from, to) },
                                        onAddTab = { viewModel.addNewTab() },
                                        onOpenDrawer = { scope.launch { drawerState.open() } }
                                    )
                                }
                            }

                            Column {
                                AnimatedVisibility(
                                    visible = selectedFiles.isNotEmpty(),
                                    enter = fadeIn() + slideInVertically(),
                                    exit = fadeOut() + slideOutVertically()
                                ) {
                                    TopAppBar(
                                        title = { Text("${selectedFiles.size}", style = MaterialTheme.typography.titleMedium) },
                                        navigationIcon = { IconButton(onClick = { viewModel.clearSelection() }) { Icon(Icons.Default.Close, contentDescription = "Close") } },
                                        actions = {
                                            TooltipIconButton("Info", Icons.Default.Info) { 
                                                selectedFiles.firstOrNull()?.let { viewModel.showInfo(it) }
                                            }
                                            TooltipIconButton("Copy", Icons.Default.ContentCopy) { scope.launch { snackbarHostState.showSnackbar("Copied") } }
                                            TooltipIconButton("Move", Icons.Default.ContentCut) { scope.launch { snackbarHostState.showSnackbar("Cut") } }
                                            TooltipIconButton("Delete", Icons.Default.Delete) { viewModel.showDeleteConfirmation() }
                                            TooltipIconButton("Rename", Icons.Default.Edit) { scope.launch { snackbarHostState.showSnackbar("Coming soon") } }
                                            TooltipIconButton("Archive", Icons.Default.Archive) { scope.launch { snackbarHostState.showSnackbar("Coming soon") } }
                                            TooltipIconButton("Select All", Icons.Default.SelectAll) { viewModel.selectAll() }
                                        },
                                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                                    )
                                }
                            }
                        }
                        if (isProcessing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            bottomBar = {
                var bottomSwipeAccumulator by remember { mutableFloatStateOf(0f) }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.graphicsLayer {
                        alpha = barVisibility
                        translationY = (1f - barVisibility) * 100f
                    }.pointerInput(activeTabIndex, tabs.size) {
                        detectHorizontalDragGestures(
                            onDragEnd = { bottomSwipeAccumulator = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                bottomSwipeAccumulator += dragAmount
                                if (abs(bottomSwipeAccumulator) > 40f) {
                                    if (bottomSwipeAccumulator > 0) { // Previous
                                        if (activeTabIndex > 0) {
                                            viewModel.switchTab(activeTabIndex - 1)
                                        } else {
                                            scope.launch { drawerState.open() }
                                        }
                                        bottomSwipeAccumulator = 0f
                                    } else if (bottomSwipeAccumulator < 0) { // Next
                                        if (activeTabIndex < tabs.size - 1) {
                                            viewModel.switchTab(activeTabIndex + 1)
                                        } else {
                                            viewModel.addNewTab()
                                        }
                                        bottomSwipeAccumulator = 0f
                                    }
                                }
                            }
                        )
                    }
                ) {
                    Column(modifier = Modifier.navigationBarsPadding()) {
                        BottomAppBar(
                            containerColor = Color.Transparent, 
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                TooltipIconButton("Tabs", Icons.Default.Tab) { viewModel.addNewTab() }
                                TooltipIconButton("New", Icons.Default.Add) { scope.launch { snackbarHostState.showSnackbar("Coming soon") } }
                                TooltipIconButton("Refresh", Icons.Default.Refresh) { viewModel.refresh(); scope.launch { snackbarHostState.showSnackbar("Refreshed") } }
                                TooltipIconButton("Select All", Icons.Default.SelectAll) { viewModel.selectAll() }
                                TooltipIconButton("Sort", Icons.Default.SortByAlpha) { viewModel.showSortOptions() }
                                TooltipIconButton("View", Icons.Default.GridView) { viewModel.showViewOptions() }
                                Box {
                                    TooltipIconButton("More", Icons.Default.MoreVert) { showMoreMenu = true }
                                    DropdownMenu(
                                        expanded = showMoreMenu,
                                        onDismissRequest = { showMoreMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Create") },
                                            onClick = { 
                                                showMoreMenu = false
                                                viewModel.showCreateDialog()
                                            },
                                            leadingIcon = { Icon(Icons.Default.Add, null) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            val boundaryScrollConnection = remember {
                object : NestedScrollConnection {
                    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                        val delta = available.x
                        if (source == NestedScrollSource.Drag && abs(delta) > 10f) {
                            if (delta > 10f && activeTabIndex == 0) {
                                scope.launch { drawerState.open() }
                                return available
                            } else if (delta < -10f && activeTabIndex == tabs.size - 1) {
                                viewModel.addNewTab()
                                return available
                            }
                        }
                        return Offset.Zero
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize().nestedScroll(boundaryScrollConnection)) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    pageSpacing = 0.dp
                ) { pageIndex ->
                    val tab = tabs.getOrNull(pageIndex) ?: return@HorizontalPager
                    val isSearching = (isDiscoveryMode || currentFilter != FilterMode.None) && pageIndex == activeTabIndex
                    val hasInput = searchQuery.isNotEmpty() || currentFilter != FilterMode.None
                    val displayFiles = if (isSearching && hasInput) searchResults else tab.files

                    val pullToRefreshState = rememberPullToRefreshState(positionalThreshold = 56.dp)
                    
                    if (pullToRefreshState.isRefreshing && pageIndex == activeTabIndex) {
                        LaunchedEffect(true) { 
                            viewModel.refresh()
                        }
                    }

                    // Sync refresh state with viewModel's isProcessing
                    LaunchedEffect(isProcessing) {
                        if (!isProcessing && pullToRefreshState.isRefreshing) {
                            pullToRefreshState.endRefresh()
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize().nestedScroll(pullToRefreshState.nestedScrollConnection)) {
                        if (viewMode == ViewMode.Grid || viewMode == ViewMode.Gallery) {
                            LazyVerticalGrid(
                                state = if (pageIndex == activeTabIndex) lazyGridState else rememberLazyGridState(),
                                columns = GridCells.Fixed(4),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    top = paddingValues.calculateTopPadding() + 8.dp,
                                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 80.dp,
                                    start = if (viewMode == ViewMode.Gallery) 0.dp else 8.dp,
                                    end = if (viewMode == ViewMode.Gallery) 0.dp else 8.dp
                                ),
                                horizontalArrangement = Arrangement.spacedBy(if (viewMode == ViewMode.Gallery) 0.dp else 2.dp),
                                verticalArrangement = Arrangement.spacedBy(if (viewMode == ViewMode.Gallery) 0.dp else 2.dp)
                            ) {
                                items(displayFiles, key = { it.absolutePath }) { file ->
                                    if (viewMode == ViewMode.Gallery) {
                                        FileItemGallery(file, selectedFiles.contains(file), viewModel,
                                            onClick = { if (selectedFiles.isNotEmpty()) viewModel.toggleSelection(file) else viewModel.openDirectory(file) },
                                            onLongClick = { viewModel.toggleSelection(file) })
                                    } else {
                                        FileItemGrid(file, selectedFiles.contains(file), viewMode, viewModel,
                                            onClick = { if (selectedFiles.isNotEmpty()) viewModel.toggleSelection(file) else viewModel.openDirectory(file) },
                                            onLongClick = { viewModel.toggleSelection(file) })
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                state = if (pageIndex == activeTabIndex) lazyListState else rememberLazyListState(),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    top = paddingValues.calculateTopPadding(),
                                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 80.dp
                                )
                            ) {
                                items(displayFiles, key = { it.absolutePath }) { file ->
                                    FileItemExpressive(file, selectedFiles.contains(file), viewMode, viewModel,
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

                        PullToRefreshContainer(
                            state = pullToRefreshState,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = paddingValues.calculateTopPadding())
                        )

                        if (pageIndex == activeTabIndex) {
                            AnimatedVisibility(visible = isDiscoveryMode && selectedFiles.isEmpty(), enter = slideInVertically(initialOffsetY = { it }) + fadeIn(), exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
                                Surface(tonalElevation = 8.dp, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding(), color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
                                    SearchSubBar(query = searchQuery, options = searchOptions, currentFilter = currentFilter, onQueryChange = { viewModel.updateSearchQuery(it) }, onFilterChange = { viewModel.applyFilter(it) }, onOptionsChange = { viewModel.updateSearchOptions(it) }, onExit = { viewModel.exitDiscoveryMode() })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
