package com.wise.file_manager

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import android.app.Activity
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: FileViewModel) {
    // System Bar Controller
    val view = LocalView.current
    val window = (view.context as Activity).window
    val insetsController = remember { WindowCompat.getInsetsController(window, view) }

    LaunchedEffect(Unit) {
        // We might want to keep status bar visible on Home, 
        // but the user requested to hide them. 
        // Let's show them on Home but ensure they are transparent (already done in theme)
        insetsController.show(WindowInsetsCompat.Type.statusBars())
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { Text("Wise File Manager", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            Text(
                "Quick Access",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(viewModel.categories) { category ->
                    HomeShortcutItem(
                        title = category.title,
                        icon = getIconForType(category.icon),
                        color = getCategoryColor(category.filterMode),
                        onClick = {
                            if (category.filterMode != FilterMode.None) {
                                viewModel.applyFilter(category.filterMode)
                                viewModel.navigateToScreen(AppScreen.Explorer)
                            } else if (category.path.isNotEmpty()) {
                                viewModel.navigateToPath(category.path)
                                viewModel.navigateToScreen(AppScreen.Explorer)
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            Text(
                "Specialized Tools",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val recentPdfs by viewModel.recentPdfs.collectAsState()
                
                ToolShortcutItem(
                    title = "Resume PDF",
                    icon = Icons.Default.MenuBook,
                    color = Color(0xFFE91E63),
                    modifier = Modifier.weight(1f),
                    onClick = { 
                        if (recentPdfs.isNotEmpty()) {
                            viewModel.updatePreviewFile(recentPdfs.first())
                            viewModel.navigateToScreen(AppScreen.PdfViewer)
                        } else {
                            viewModel.loadPdfLibrary()
                            viewModel.navigateToScreen(AppScreen.PdfViewer)
                        }
                    }
                )
                ToolShortcutItem(
                    title = "PDF Library",
                    icon = Icons.Default.PictureAsPdf,
                    color = Color(0xFFF44336),
                    modifier = Modifier.weight(1f),
                    onClick = { 
                        viewModel.loadPdfLibrary()
                        viewModel.navigateToScreen(AppScreen.PdfViewer) 
                    }
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ToolShortcutItem(
                    title = "Music Player",
                    icon = Icons.Default.MusicNote,
                    color = Color(0xFF9C27B0),
                    modifier = Modifier.weight(1f),
                    onClick = { 
                        viewModel.applyFilter(FilterMode.Audio)
                        viewModel.navigateToScreen(AppScreen.Explorer)
                    }
                )
                ToolShortcutItem(
                    title = "Video Player",
                    icon = Icons.Default.PlayCircle,
                    color = Color(0xFF2196F3),
                    modifier = Modifier.weight(1f),
                    onClick = { 
                        viewModel.applyFilter(FilterMode.Videos)
                        viewModel.navigateToScreen(AppScreen.Explorer)
                    }
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ToolShortcutItem(
                    title = "File Explorer",
                    icon = Icons.Default.Folder,
                    color = Color(0xFFFF9800),
                    modifier = Modifier.weight(1f),
                    onClick = { 
                        viewModel.navigateToScreen(AppScreen.Explorer)
                    }
                )
                // Placeholder to keep Row balanced or use a different layout
                Box(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun HomeShortcutItem(title: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = title, tint = color, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
fun ToolShortcutItem(title: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.height(100.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = title, tint = color, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

fun getCategoryColor(filter: FilterMode): Color {
    return when (filter) {
        FilterMode.Images -> Color(0xFF4CAF50)
        FilterMode.Videos -> Color(0xFF2196F3)
        FilterMode.Audio -> Color(0xFF9C27B0)
        FilterMode.Apks -> Color(0xFF009688)
        FilterMode.Archives -> Color(0xFFFF9800)
        FilterMode.Documents -> Color(0xFFF44336)
        else -> Color(0xFF607D8B)
    }
}
