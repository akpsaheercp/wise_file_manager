package com.wise.file_manager

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import android.util.Xml
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commit
import androidx.pdf.viewer.fragment.PdfViewerFragment
import androidx.compose.ui.platform.LocalView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipFile
import kotlin.math.roundToInt

private val pdfThumbnailCache = LruCache<String, Bitmap>(50) // Cache last 50 PDF thumbnails

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerPage(viewModel: FileViewModel, file: File, onBack: () -> Unit) {
    var isUiVisible by remember { mutableStateOf(true) }
    var isNightMode by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // System Bar Controller
    val view = LocalView.current
    val window = (view.context as Activity).window
    val insetsController = remember { WindowCompat.getInsetsController(window, view) }

    LaunchedEffect(isUiVisible) {
        if (!isUiVisible) {
            insetsController.hide(WindowInsetsCompat.Type.statusBars())
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            insetsController.show(WindowInsetsCompat.Type.statusBars())
        }
    }

    val pdfHistory by viewModel.pdfHistory.collectAsState()
    val recentPdfs by viewModel.recentPdfs.collectAsState()
    val pdfLibrary by viewModel.pdfLibrary.collectAsState()

    var showRecentSheet by remember { mutableStateOf(false) }
    var showLibrarySheet by remember { mutableStateOf(false) }

    // Resume reading logic
    LaunchedEffect(file) {
        viewModel.updatePdfHistory(file.absolutePath, 0)
    }

    val headerColor = MaterialTheme.colorScheme.surfaceContainer
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    BackHandler { onBack() }

    // System Bar Integration
    LaunchedEffect(isUiVisible, isNightMode) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        if (isUiVisible) {
            controller.show(WindowInsetsCompat.Type.statusBars())
            controller.show(WindowInsetsCompat.Type.navigationBars())
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
        } else {
            controller.hide(WindowInsetsCompat.Type.statusBars())
            controller.hide(WindowInsetsCompat.Type.navigationBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        
        controller.isAppearanceLightStatusBars = !isNightMode
        controller.isAppearanceLightNavigationBars = !isNightMode
    }

    // Auto-hide UI
    LaunchedEffect(isUiVisible) {
        if (isUiVisible) {
            delay(5000)
            isUiVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isNightMode) Color(0xFF121212) else MaterialTheme.colorScheme.surfaceContainerLowest)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { isUiVisible = !isUiVisible }
                )
            }
    ) {
        // PDF Content
        PdfPreviewBestPractice(
            file = file,
            isUiVisible = isUiVisible,
            onSetUiVisible = { isUiVisible = it }
        )

        // Header Overlay
        AnimatedVisibility(
            visible = isUiVisible,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                tonalElevation = 12.dp,
                shadowElevation = 8.dp,
                color = headerColor.copy(alpha = 0.95f)
            ) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = file.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1
                            )
                            Text(
                                text = FileUtils.formatFileSize(file.length()),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { isNightMode = !isNightMode }) {
                            Icon(
                                if (isNightMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Night Mode"
                            )
                        }
                        IconButton(onClick = { /* Share */ }) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                    },
                    windowInsets = WindowInsets.statusBars,
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        }

        // Bottom Bar Overlay
        AnimatedVisibility(
            visible = isUiVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = headerColor.copy(alpha = 0.98f),
                tonalElevation = 12.dp,
                shadowElevation = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showRecentSheet = true }) {
                        Icon(Icons.Default.History, contentDescription = "Recent")
                    }

                    Text(
                        text = "Interactive Reader",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(onClick = { 
                        viewModel.loadPdfLibrary()
                        showLibrarySheet = true 
                    }) {
                        Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = "Library")
                    }
                }
            }
        }

        // Fixed-Position Overlay Sheets (Now inside the Box to support scrim correctly)
        PdfListSheet(
            visible = showRecentSheet,
            title = "Recent Documents",
            files = recentPdfs,
            isGrid = true,
            onDismiss = { showRecentSheet = false },
            onFileClick = { 
                viewModel.openDirectory(it)
                showRecentSheet = false 
            }
        )

        PdfListSheet(
            visible = showLibrarySheet,
            title = "PDF Library",
            files = pdfLibrary,
            isGrid = true,
            onDismiss = { showLibrarySheet = false },
            onFileClick = { 
                viewModel.openDirectory(it)
                showLibrarySheet = false 
            }
        )
    }
}

@Composable
fun PdfPreviewBestPractice(
    file: File,
    isUiVisible: Boolean,
    onSetUiVisible: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val fragmentActivity = context as? FragmentActivity
    val fragmentManager = fragmentActivity?.supportFragmentManager
    val containerId = remember { View.generateViewId() }
    
    val currentUiVisible by rememberUpdatedState(isUiVisible)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Use Initial pass to see the tap before the AndroidView/Fragment consumes it
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val isPrimaryDown = event.changes.all { it.changedToDown() }
                        
                        if (isPrimaryDown) {
                            // Start tracking a potential tap
                            val down = event.changes.first()
                            val up = withTimeoutOrNull(200) {
                                // Wait for Up on the same Initial pass
                                var lastUp: PointerInputChange? = null
                                while (lastUp == null) {
                                    val nextEvent = awaitPointerEvent(PointerEventPass.Initial)
                                    if (nextEvent.changes.all { it.changedToUp() }) {
                                        lastUp = nextEvent.changes.first()
                                    } else if (nextEvent.changes.any { it.positionChange().getDistance() > 10 }) {
                                        // Moved too much, cancel tap
                                        return@withTimeoutOrNull null
                                    }
                                }
                                lastUp
                            }
                            
                            if (up != null) {
                                // Valid single tap detected
                                onSetUiVisible(!currentUiVisible)
                            }
                        }
                    }
                }
            }
    ) {
        if (fragmentManager != null) {
            AndroidView(
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        id = containerId
                        val fragment = PdfViewerFragment().apply {
                            documentUri = Uri.fromFile(file)
                        }
                        fragmentManager.commit {
                            replace(id, fragment, "pdf_viewer_${file.name}")
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Please use FragmentActivity to enable interactive PDF viewing")
            }
        }
    }
}

@Composable
fun PdfListSheet(
    visible: Boolean,
    title: String,
    files: List<File>,
    isGrid: Boolean = false,
    onDismiss: () -> Unit,
    onFileClick: (File) -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val sheetHeight = screenHeight / 2
    
    val isLibrary = title.contains("Library", ignoreCase = true)
    var selectedFolderPath by remember(visible) { mutableStateOf<String?>(null) }

    val groupedFiles = remember(files) {
        files.groupBy { it.parentFile?.absolutePath ?: "Storage" }
    }

    if (visible) {
        BackHandler { onDismiss() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Custom Scrim - Closes when tapping the PDF page outside the window
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { onDismiss() })
                    }
            )
        }

        // Fixed Window - Sticks in position, slides in/out from bottom
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sheetHeight)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* Consumes clicks to prevent closing when tapping inside the window */ },
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 12.dp,
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 24.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 16.dp)
                    ) {
                        if (selectedFolderPath != null) {
                            IconButton(
                                onClick = { selectedFolderPath = null },
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                        Text(
                            text = if (selectedFolderPath != null) File(selectedFolderPath!!).name else title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                    
                    Box(modifier = Modifier.weight(1f)) {
                        if (files.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No files found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else if (isGrid) {
                            if (isLibrary && selectedFolderPath == null) {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(6),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(groupedFiles.keys.toList()) { path ->
                                        val folderFile = File(path)
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { selectedFolderPath = path },
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Surface(
                                                modifier = Modifier.aspectRatio(1f).fillMaxWidth(),
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        Icons.Default.Folder,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(32.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                    Text(
                                                        text = groupedFiles[path]?.size.toString(),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = folderFile.name,
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            } else {
                                val displayFiles = if (selectedFolderPath != null) {
                                    groupedFiles[selectedFolderPath] ?: emptyList()
                                } else {
                                    files
                                }

                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(6),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(displayFiles) { file ->
                                        PdfGridItem(file, onFileClick)
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(files) { file ->
                                    Surface(
                                        onClick = { onFileClick(file) },
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainer
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                if (file.extension.lowercase() == "pdf") Icons.Default.PictureAsPdf 
                                                else Icons.AutoMirrored.Filled.MenuBook,
                                                contentDescription = null,
                                                tint = if (file.extension.lowercase() == "pdf") Color(0xFFF44336) else Color(0xFF2196F3)
                                            )
                                            Spacer(Modifier.width(16.dp))
                                            Column {
                                                Text(
                                                    file.name,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    FileUtils.formatFileSize(file.length()),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
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
fun PdfGridItem(file: File, onFileClick: (File) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onFileClick(file) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier
                .aspectRatio(0.707f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 2.dp,
            shadowElevation = 2.dp
        ) {
            if (file.extension.lowercase() == "pdf") {
                PdfThumbnail(file = file, size = 60.dp)
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Color(0xFF2196F3)
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = file.name,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun PdfThumbnail(file: File, size: androidx.compose.ui.unit.Dp) {
    var thumbnail by remember(file) { mutableStateOf(pdfThumbnailCache.get(file.absolutePath)) }
    
    if (thumbnail == null) {
        LaunchedEffect(file) {
            delay(100) // Small delay to prioritize scroll performance
            withContext(Dispatchers.IO) {
                try {
                    val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(pfd)
                    if (renderer.pageCount > 0) {
                        val page = renderer.openPage(0)
                        val bitmap = Bitmap.createBitmap(page.width / 4, page.height / 4, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        pdfThumbnailCache.put(file.absolutePath, bitmap)
                        thumbnail = bitmap
                        page.close()
                    }
                    renderer.close(); pfd.close()
                } catch (e: Exception) { }
            }
        }
    }
    
    thumbnail?.let { Image(bitmap = it.asImageBitmap(), null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        ?: Icon(Icons.Default.PictureAsPdf, null, tint = Color(0xFFF44336), modifier = Modifier.size(size * 0.6f))
}

@Composable
fun FilePreviewDialog(file: File, onDismiss: () -> Unit) {
    var forceTextMode by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black.copy(alpha = 0.95f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White) }
                    Text(file.name, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 16.dp))
                    if (file.extension.lowercase() !in listOf("pdf", "mp4", "mkv", "webm", "avi", "3gp", "mp3", "wav", "flac", "m4a", "jpg", "jpeg", "png", "gif", "webp")) {
                        IconButton(onClick = { forceTextMode = !forceTextMode }) { Icon(Icons.AutoMirrored.Filled.Article, contentDescription = "Toggle Text Mode", tint = Color.White) }
                    }
                }
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val extension = file.extension.lowercase()
                    when {
                        forceTextMode -> TextPreview(file)
                        FileUtils.isVideo(extension) -> VideoPreview(file)
                        FileUtils.isDocx(extension) -> DocxPreview(file)
                        FileUtils.isText(extension) -> TextPreview(file)
                        else -> UnsupportedPreview(file, onOpenAsText = { forceTextMode = true })
                    }
                }
            }
        }
    }
}

@Composable
fun VideoPreview(file: File) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(file.absolutePath))
            prepare()
        }
    }
    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }
    androidx.compose.ui.viewinterop.AndroidView(factory = { PlayerView(it).apply { player = exoPlayer } }, modifier = Modifier.fillMaxSize())
}

@Composable
fun DocxPreview(file: File) {
    val content = remember(file) {
        try {
            val zipFile = ZipFile(file)
            val entry = zipFile.getEntry("word/document.xml")
            if (entry != null) {
                val inputStream = zipFile.getInputStream(entry)
                val parser = Xml.newPullParser()
                parser.setInput(inputStream, "UTF-8")
                val result = StringBuilder()
                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && parser.name == "t") result.append(parser.nextText())
                    else if (eventType == XmlPullParser.END_TAG && parser.name == "p") result.append("\n")
                    eventType = parser.next()
                }
                inputStream.close(); zipFile.close(); result.toString()
            } else { zipFile.close(); "Invalid .docx" }
        } catch (e: Exception) { "Error: ${e.message}" }
    }
    Box(modifier = Modifier.fillMaxSize().background(Color.White).padding(24.dp).verticalScroll(rememberScrollState())) {
        Text(text = content, color = Color.Black, style = MaterialTheme.typography.bodyLarge, lineHeight = 28.sp)
    }
}

@Composable
fun TextPreview(file: File) {
    val text = remember(file) {
        try {
            val size = file.length().coerceAtMost(51200).toInt()
            val buffer = ByteArray(size)
            val fis = FileInputStream(file)
            fis.read(buffer); fis.close()
            String(buffer)
        } catch (e: Exception) { "Error: ${e.message}" }
    }
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A1A)).padding(16.dp).verticalScroll(rememberScrollState())) {
        androidx.compose.foundation.text.selection.SelectionContainer {
            Text(text = text, fontFamily = FontFamily.Monospace, color = Color(0xFFE0E0E0), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun UnsupportedPreview(file: File, onOpenAsText: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(80.dp), tint = Color.Gray)
        Spacer(Modifier.height(24.dp))
        Text(text = "No preview for .${file.extension}", color = Color.White, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onOpenAsText, shape = RoundedCornerShape(16.dp)) { Text("Open as Text") }
    }
}
