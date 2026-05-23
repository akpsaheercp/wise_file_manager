package com.wise.file_manager

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs

import android.app.Activity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ImageViewerScreen(viewModel: FileViewModel, file: File, onBack: () -> Unit) {
    val imagePlaylist by viewModel.imagePlaylist.collectAsState()
    val initialIndex = remember(imagePlaylist) { imagePlaylist.indexOf(file).coerceAtLeast(0) }
    val pagerState = rememberPagerState(initialPage = initialIndex) { imagePlaylist.size }
    val scope = rememberCoroutineScope()
    
    var isControlsVisible by remember { mutableStateOf(true) }

    // System Bar Controller
    val view = LocalView.current
    val window = (view.context as Activity).window
    val insetsController = remember { WindowCompat.getInsetsController(window, view) }

    LaunchedEffect(isControlsVisible) {
        if (!isControlsVisible) {
            insetsController.hide(WindowInsetsCompat.Type.statusBars())
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            insetsController.show(WindowInsetsCompat.Type.statusBars())
        }
    }

    // Track zoom state for each page to enable/disable pager scroll
    val pageZoomStates = remember { mutableStateMapOf<Int, Boolean>() }
    val isZoomed = pageZoomStates[pagerState.currentPage] ?: false

    val currentFile = if (imagePlaylist.isNotEmpty()) imagePlaylist[pagerState.currentPage] else file

    LaunchedEffect(pagerState.currentPage) {
        if (imagePlaylist.isNotEmpty()) {
            viewModel.updatePreviewFile(imagePlaylist[pagerState.currentPage])
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AnimatedVisibility(
                visible = isControlsVisible,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                TopAppBar(
                    modifier = Modifier.statusBarsPadding(),
                    title = {
                        Column {
                            Text(
                                text = currentFile.name,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (imagePlaylist.size > 1) {
                                Text(
                                    text = "${pagerState.currentPage + 1} of ${imagePlaylist.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { /* Info */ }) {
                            Icon(Icons.Default.Info, contentDescription = "Info")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = 16.dp,
                userScrollEnabled = !isZoomed
            ) { page ->
                val imageFile = imagePlaylist[page]
                var scale by remember { mutableFloatStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }

                LaunchedEffect(scale) {
                    pageZoomStates[page] = scale > 1f
                }

                LaunchedEffect(pagerState.currentPage) {
                    if (pagerState.currentPage != page) {
                        scale = 1f
                        offset = Offset.Zero
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(scale) {
                            detectTransformGestures(
                                onGesture = { _, pan, zoom, _ ->
                                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                                    // Only consume and apply transformations if we are already zoomed 
                                    // or if we are starting a zoom gesture
                                    if (scale > 1f || zoom != 1f) {
                                        scale = newScale
                                        offset += pan
                                    }
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { isControlsVisible = !isControlsVisible }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = imageFile,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            },
                        contentScale = ContentScale.Fit
                    )
                }
            }

            // Navigation Buttons (Floating)
            AnimatedVisibility(
                visible = isControlsVisible && imagePlaylist.size > 1,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (pagerState.currentPage > 0) {
                        FilledIconButton(
                            onClick = { 
                                scope.launch { 
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1) 
                                } 
                            },
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 16.dp)
                                .size(48.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = CircleShape
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous")
                        }
                    }

                    if (pagerState.currentPage < imagePlaylist.size - 1) {
                        FilledIconButton(
                            onClick = { 
                                scope.launch { 
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1) 
                                } 
                            },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 16.dp)
                                .size(48.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = CircleShape
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Next")
                        }
                    }
                }
            }

            // Bottom Actions
            AnimatedVisibility(
                visible = isControlsVisible,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
            ) {
                Surface(
                    modifier = Modifier
                        .padding(bottom = 32.dp)
                        .clip(RoundedCornerShape(24.dp)),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f),
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { /* Edit */ }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { /* Share */ }) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                        IconButton(onClick = { viewModel.showDeleteConfirmation() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
