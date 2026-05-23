package com.wise.file_manager

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExplorerTabBar(
    tabs: List<ExplorerTab>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    onTabClick: (Int) -> Unit,
    onTabClose: (Int) -> Unit,
    onMoveTab: (Int, Int) -> Unit,
    onAddTab: () -> Unit,
    onOpenDrawer: () -> Unit
) {
    val activeIndex = pagerState.currentPage
    var swipeAccumulator by remember { mutableFloatStateOf(0f) }
    var draggingTabIndex by remember { mutableStateOf<Int?>(null) }

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
                    if (draggingTabIndex == null) {
                        swipeAccumulator += dragAmount
                        if (abs(swipeAccumulator) > 100f) {
                            if (swipeAccumulator > 0) { // Previous
                                if (activeIndex > 0) onTabClick(activeIndex - 1) else onOpenDrawer()
                                swipeAccumulator = 0f
                            } else if (swipeAccumulator < 0) { // Next
                                if (activeIndex < tabs.size - 1) onTabClick(activeIndex + 1) else onAddTab()
                                swipeAccumulator = 0f
                            }
                        }
                    }
                }
            )
        },
        indicator = { tabPositions ->
            if (draggingTabIndex == null && tabPositions.isNotEmpty()) {
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[activeIndex]),
                    height = 3.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) {
        tabs.forEachIndexed { index, tab ->
            val lastSegment = tab.path.split("/").last().ifEmpty { "Root" }
            val folderName = if (lastSegment == "0") "Device Storage" else lastSegment
            
            Tab(
                selected = activeIndex == index,
                onClick = { onTabClick(index) },
                modifier = Modifier.combinedClickable(
                    onClick = { onTabClick(index) },
                    onLongClick = { draggingTabIndex = index }
                ),
                text = {
                    val isDragging = draggingTabIndex == index
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .graphicsLayer {
                                alpha = if (isDragging) 0.5f else 1.0f
                                scaleX = if (isDragging) 1.1f else 1.0f
                                scaleY = if (isDragging) 1.1f else 1.0f
                            }
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (activeIndex == index && !isDragging) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                else if (isDragging) MaterialTheme.colorScheme.secondaryContainer
                                else Color.Transparent
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .pointerInput(index) {
                                if (draggingTabIndex == index) {
                                    detectHorizontalDragGestures(
                                        onDragEnd = { draggingTabIndex = null },
                                        onHorizontalDrag = { change, dragAmount ->
                                            val threshold = 50f
                                            if (dragAmount > threshold && index < tabs.size - 1) {
                                                onMoveTab(index, index + 1)
                                                draggingTabIndex = index + 1
                                            } else if (dragAmount < -threshold && index > 0) {
                                                onMoveTab(index, index - 1)
                                                draggingTabIndex = index - 1
                                            }
                                        }
                                    )
                                }
                            }
                    ) {
                        Text(
                            text = folderName,
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 10.sp),
                            fontWeight = if (activeIndex == index) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (tabs.size > 1) {
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = { onTabClose(index) },
                                modifier = Modifier.size(14.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close, 
                                    null, 
                                    modifier = Modifier.size(10.dp),
                                    tint = if (activeIndex == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
            size = 140.dp,
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
            modifier = Modifier.fillMaxSize().padding(2.dp),
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
                delay(300) // Debounce calculation during scroll
                val dbCached = viewModel.getCachedFolderInfo(file.absolutePath)
                if (dbCached != null && dbCached.childCount.isNotEmpty()) {
                    value = dbCached.childCount
                    viewModel.updateFolderSizeCache(file.absolutePath, dbCached.childCount)
                } else {
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
            
            Column(modifier = Modifier.weight(if (viewMode == ViewMode.Columned) 0.5f else 1f)) {
                Text(file.name, style = if (viewMode == ViewMode.Compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge, fontWeight = if (file.isDirectory) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (viewMode == ViewMode.Detailed) {
                    val date = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(file.lastModified()))
                    val details = if (file.isDirectory) folderInfo else FileUtils.formatFileSize(file.length())
                    Text("$details • $date", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (viewMode == ViewMode.Columned) {
                Text(
                    text = SimpleDateFormat("MMM dd, yy", Locale.getDefault()).format(Date(file.lastModified())),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.25f),
                    textAlign = TextAlign.End
                )
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
            .clip(RoundedCornerShape(if (size > 80.dp) 16.dp else size/4))
            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
    ) {
        when {
            file.isDirectory -> Icon(Icons.Default.Folder, null, tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary, modifier = Modifier.size(size * 0.6f))
            FileUtils.isImage(extension) || FileUtils.isVideo(extension) -> {
                AsyncImage(model = ImageRequest.Builder(context).data(file).crossfade(true).build(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, error = androidx.compose.ui.graphics.painter.ColorPainter(Color.Transparent))
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
