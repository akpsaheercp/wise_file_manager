package com.wise.file_manager

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import kotlin.math.abs

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.app.Activity

import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState

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
    
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    // System Bar Controller
    val view = LocalView.current
    val window = (view.context as Activity).window
    val insetsController = remember { WindowCompat.getInsetsController(window, view) }

    // Scroll States
    val lazyGridState = rememberLazyGridState()
    val lazyListState = rememberLazyListState()

    val pagerState = rememberPagerState(initialPage = activeTabIndex) { tabs.size }
    val tabAnimationSpec = remember { tween<Float>(durationMillis = 300, easing = FastOutSlowInEasing) }

    LaunchedEffect(activeTabIndex) {
        if (pagerState.currentPage != activeTabIndex && activeTabIndex in 0 until tabs.size) {
            pagerState.animateScrollToPage(
                page = activeTabIndex,
                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
            )
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != activeTabIndex) {
            viewModel.switchTab(pagerState.currentPage)
        }
    }

    // Scroll-linked Visibility (0f = hidden, 1f = visible)
    var barVisibility by remember { mutableStateOf(1f) }

    // Sync System Bars with UI Bar Visibility
    LaunchedEffect(barVisibility) {
        if (barVisibility <= 0.2f) {
            insetsController.hide(WindowInsetsCompat.Type.statusBars())
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            insetsController.show(WindowInsetsCompat.Type.statusBars())
        }
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
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (selectedFiles.isEmpty() && !isDiscoveryMode) {
                    // Gradual hide/show over 200px of scroll
                    val newVisibility = (barVisibility + delta / 400f).coerceIn(0f, 1f)
                    barVisibility = newVisibility
                }
                return Offset.Zero
            }
        }
    }

    // Reset visibility when at top or selection starts
    LaunchedEffect(isAtTop, selectedFiles.isNotEmpty(), isDiscoveryMode) {
        if (isAtTop || selectedFiles.isNotEmpty() || isDiscoveryMode) {
            barVisibility = 1f
        }
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

    // Back Navigation Handler
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
            contentWindowInsets = WindowInsets(0, 0, 0, 0), // Full bleed
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = barVisibility * 0.95f),
                    tonalElevation = 2.dp,
                    modifier = Modifier.graphicsLayer {
                        alpha = barVisibility
                        translationY = (1f - barVisibility) * -100f
                    }
                ) {
                    Column(modifier = Modifier.statusBarsPadding()) {
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
                                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                            )
                        } else {
                            Column {
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
                                    },
                                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                                )
                                // Tab Bar
                                if (tabs.size > 1) {
                                    ExplorerTabBar(
                                        tabs = tabs,
                                        activeIndex = activeTabIndex,
                                        onTabClick = { viewModel.switchTab(it) },
                                        onTabClose = { viewModel.closeTab(it) },
                                        onAddTab = { viewModel.addNewTab() },
                                        onOpenDrawer = { scope.launch { drawerState.open() } }
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
                    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = barVisibility * 0.95f),
                    modifier = Modifier.graphicsLayer {
                        alpha = barVisibility
                        translationY = (1f - barVisibility) * 100f
                    }.pointerInput(activeTabIndex, tabs.size) {
                        detectHorizontalDragGestures(
                            onDragEnd = { bottomSwipeAccumulator = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                bottomSwipeAccumulator += dragAmount
                                if (abs(bottomSwipeAccumulator) > 40f) { // Reduced threshold for better responsiveness
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
                                TooltipIconButton("More", Icons.Default.MoreVert) { }
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
                    pageSpacing = 0.dp // Ensure seamless transition
                ) { pageIndex ->
                val tab = tabs.getOrNull(pageIndex) ?: return@HorizontalPager
                val isSearching = (isDiscoveryMode || currentFilter != FilterMode.None) && pageIndex == activeTabIndex
                val hasInput = searchQuery.isNotEmpty() || currentFilter != FilterMode.None
                val displayFiles = if (isSearching && hasInput) searchResults else tab.files

                val pullToRefreshState = rememberPullToRefreshState(
                    positionalThreshold = 40.dp
                )
                
                if (pullToRefreshState.isRefreshing && pageIndex == activeTabIndex) {
                    LaunchedEffect(true) {
                        viewModel.refresh()
                    }
                }

                LaunchedEffect(isProcessing, pageIndex == activeTabIndex) {
                    if (pageIndex == activeTabIndex) {
                        if (isProcessing) {
                            if (!pullToRefreshState.isRefreshing) pullToRefreshState.startRefresh()
                        } else {
                            pullToRefreshState.endRefresh()
                        }
                    }
                }

                Box(modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(pullToRefreshState.nestedScrollConnection)
                ) {
                    // Adaptive File List based on ViewMode
                    if (viewMode == ViewMode.Grid || viewMode == ViewMode.Gallery) {
                        val columns = 4
                        val spacing = if (viewMode == ViewMode.Gallery) 0.dp else 2.dp
                        
                        LazyVerticalGrid(
                            state = if (pageIndex == activeTabIndex) lazyGridState else rememberLazyGridState(),
                            columns = GridCells.Fixed(columns),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                top = paddingValues.calculateTopPadding() + 8.dp,
                                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 80.dp,
                                start = if (viewMode == ViewMode.Gallery) 0.dp else 8.dp,
                                end = if (viewMode == ViewMode.Gallery) 0.dp else 8.dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(spacing),
                            verticalArrangement = Arrangement.spacedBy(spacing)
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
                                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 80.dp,
                                start = 0.dp,
                                end = 0.dp
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

                    // Keyboard-attached Search (only on active tab)
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

@Composable
fun ExplorerTabBar(
    tabs: List<ExplorerTab>,
    activeIndex: Int,
    onTabClick: (Int) -> Unit,
    onTabClose: (Int) -> Unit,
    onAddTab: () -> Unit,
    onOpenDrawer: () -> Unit
) {
    var swipeAccumulator by remember { mutableFloatStateOf(0f) }

    ScrollableTabRow(
        selectedTabIndex = activeIndex,
        edgePadding = 8.dp,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary,
        divider = {},
        modifier = Modifier.pointerInput(activeIndex, tabs.size) {
            detectHorizontalDragGestures(
                onDragEnd = { swipeAccumulator = 0f },
                onHorizontalDrag = { change, dragAmount ->
                    swipeAccumulator += dragAmount
                    if (abs(swipeAccumulator) > 100f) {
                        if (swipeAccumulator > 0) { // Swipe Left to Right (Previous)
                            if (activeIndex > 0) {
                                onTabClick(activeIndex - 1)
                            } else {
                                onOpenDrawer()
                            }
                            swipeAccumulator = 0f
                        } else if (swipeAccumulator < 0) { // Swipe Right to Left (Next)
                            if (activeIndex < tabs.size - 1) {
                                onTabClick(activeIndex + 1)
                            } else {
                                onAddTab()
                            }
                            swipeAccumulator = 0f
                        }
                    }
                }
            )
        },
        indicator = { tabPositions ->
            if (activeIndex < tabPositions.size) {
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[activeIndex]),
                    height = 3.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) {
        tabs.forEachIndexed { index, tab ->
            val folderName = tab.path.split("/").last().ifEmpty { "Root" }
            Tab(
                selected = activeIndex == index,
                onClick = { onTabClick(index) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = folderName,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (tabs.size > 1) {
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = { onTabClose(index) },
                                modifier = Modifier.size(16.dp)
                            ) {
                                Icon(Icons.Default.Close, null, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }
            )
        }
        // Add Tab Button
        IconButton(onClick = onAddTab) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(20.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileItemGallery(file: File, isSelected: Boolean, viewModel: FileViewModel, onClick: () -> Unit, onLongClick: () -> Unit) {
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
            size = 140.dp, // Further increased from 128.dp
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun FileItemGrid(file: File, isSelected: Boolean, viewMode: ViewMode, viewModel: FileViewModel, onClick: () -> Unit, onLongClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().aspectRatio(0.85f),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
        ),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(2.dp), // Reduced padding
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                ThumbnailView(file, isSelected, size = if (viewMode == ViewMode.Gallery) 120.dp else 84.dp)
            }
            Text(file.name, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 1.dp))
        }
    }
}

@Composable
fun FileItemExpressive(file: File, isSelected: Boolean, viewMode: ViewMode, viewModel: FileViewModel, onClick: () -> Unit, onLongClick: () -> Unit) {
    val verticalPadding = when(viewMode) {
        ViewMode.Compact -> 4.dp
        ViewMode.Minimal -> 2.dp
        else -> 10.dp
    }

    val folderInfo = if (file.isDirectory && (viewMode == ViewMode.Detailed || viewMode == ViewMode.Columned)) {
        val cached = viewModel.getFolderSize(file)
        produceState(initialValue = cached, file) {
            if (cached == "...") {
                // Try to get from persistent DB first
                val dbCached = viewModel.getCachedFolderInfo(file.absolutePath)
                if (dbCached != null && dbCached.childCount.isNotEmpty()) {
                    value = dbCached.childCount
                    viewModel.updateFolderSizeCache(file.absolutePath, dbCached.childCount)
                } else {
                    // Not indexed yet, do it now
                    value = withContext(Dispatchers.IO) {
                        try {
                            val allItems = file.listFiles()
                            val files = allItems?.count { it.isFile } ?: 0
                            val folders = allItems?.count { it.isDirectory } ?: 0
                            val result = if (viewMode == ViewMode.Columned) "$files F • $folders D" else "$files files"
                            viewModel.updateFolderSizeCache(file.absolutePath, result)
                            result
                        } catch (e: Exception) { "Unknown" }
                    }
                }
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
