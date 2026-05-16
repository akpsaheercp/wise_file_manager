package com.wise.file_manager

import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.tooling.preview.Preview
import com.wise.file_manager.ui.theme.WiseFileManagerTheme
import java.io.File
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    onCategoryClick: (String) -> Unit,
    onStorageClick: () -> Unit
) {
    Scaffold(
        topBar = {
            SearchBarPlaceholder()
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            item {
                Text(
                    text = "Categories",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }

            item {
                CategoryGrid(onCategoryClick)
            }

            item {
                Text(
                    text = "Collections",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 24.dp, bottom = 12.dp)
                )
            }

            item {
                CollectionItem(
                    icon = Icons.Default.Lock,
                    title = "Safe folder",
                    onClick = {}
                )
                CollectionItem(
                    icon = Icons.Default.Star,
                    title = "Starred",
                    onClick = {}
                )
            }

            item {
                Text(
                    text = "Storage devices",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 24.dp, bottom = 12.dp)
                )
            }

            item {
                StorageCard(onStorageClick)
            }
            
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun SearchBarPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .height(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .clickable { /* Handle search */ },
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Icon(Icons.Default.Menu, contentDescription = "Menu", modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Search your files",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Gray.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, contentDescription = "Profile", modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun CategoryGrid(onCategoryClick: (String) -> Unit) {
    val categories = listOf(
        CategoryData("Downloads", Icons.Default.Download, Color(0xFF4285F4)),
        CategoryData("Images", Icons.Default.Image, Color(0xFFEA4335)),
        CategoryData("Videos", Icons.Default.Movie, Color(0xFFFBBC05)),
        CategoryData("Audio", Icons.Default.MusicNote, Color(0xFF34A853)),
        CategoryData("Documents", Icons.Default.Description, Color(0xFF4285F4)),
        CategoryData("Apps", Icons.Default.Apps, Color(0xFFFA7B17))
    )

    Column {
        for (i in categories.indices step 2) {
            Row(modifier = Modifier.fillMaxWidth()) {
                CategoryItem(categories[i], Modifier.weight(1f), onCategoryClick)
                if (i + 1 < categories.size) {
                    CategoryItem(categories[i + 1], Modifier.weight(1f), onCategoryClick)
                }
            }
        }
    }
}

data class CategoryData(val title: String, val icon: ImageVector, val color: Color)

@Composable
fun CategoryItem(data: CategoryData, modifier: Modifier, onClick: (String) -> Unit) {
    Row(
        modifier = modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick(data.title) }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(data.color.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = data.icon,
                contentDescription = data.title,
                tint = data.color,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = data.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun CollectionItem(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun StorageCard(onClick: () -> Unit) {
    val stats = try { getStorageStats() } catch (e: Exception) { StorageStats(0, 0, 0) }
    val usedPercent = if (stats.totalBytes > 0) stats.usedBytes.toFloat() / stats.totalBytes.toFloat() else 0f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.SdStorage,
            contentDescription = "Internal Storage",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Internal storage",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { usedPercent },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${formatSize(stats.freeBytes)} free",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = formatSize(stats.totalBytes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

fun getStorageStats(): StorageStats {
    val path = Environment.getExternalStorageDirectory()
    val stat = StatFs(path.path)
    val blockSize = stat.blockSizeLong
    val totalBlocks = stat.blockCountLong
    val availableBlocks = stat.availableBlocksLong
    
    val total = totalBlocks * blockSize
    val free = availableBlocks * blockSize
    val used = total - free
    
    return StorageStats(total, free, used)
}

data class StorageStats(val totalBytes: Long, val freeBytes: Long, val usedBytes: Long)

fun formatSize(size: Long): String {
    val gb = size.toDouble() / (1024 * 1024 * 1024)
    return String.format(Locale.getDefault(), "%.1f GB", gb)
}

@Preview(showBackground = true)
@Composable
fun BrowseScreenPreview() {
    WiseFileManagerTheme {
        BrowseScreen(onCategoryClick = {}, onStorageClick = {})
    }
}
