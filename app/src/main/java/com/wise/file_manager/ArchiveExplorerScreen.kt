package com.wise.file_manager

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

@Immutable
data class ArchiveEntry(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val fullPath: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveExplorerScreen(
    viewModel: FileViewModel,
    file: File,
    onBack: () -> Unit
) {
    var virtualPath by remember { mutableStateOf("") }
    var allEntries by remember { mutableStateOf<List<ArchiveEntry>>(emptyList()) }
    var currentEntries by remember { mutableStateOf<List<ArchiveEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            try {
                val zipFile = ZipFile(file)
                val entries = zipFile.entries().asSequence().map { entry ->
                    ArchiveEntry(
                        name = entry.name.split("/").filter { it.isNotEmpty() }.lastOrNull() ?: "",
                        isDirectory = entry.isDirectory,
                        size = entry.size,
                        fullPath = entry.name
                    )
                }.toList()
                allEntries = entries
                isLoading = false
            } catch (e: Exception) {
                isLoading = false
            }
        }
    }

    LaunchedEffect(virtualPath, allEntries) {
        val filtered = allEntries.filter { entry ->
            val parentPath = if (entry.fullPath.endsWith("/")) {
                entry.fullPath.dropLast(1).substringBeforeLast("/", "")
            } else {
                entry.fullPath.substringBeforeLast("/", "")
            }
            
            val isImmediateChild = if (virtualPath.isEmpty()) {
                !entry.fullPath.contains("/") || (entry.fullPath.endsWith("/") && entry.fullPath.count { it == '/' } == 1)
            } else {
                parentPath == virtualPath
            }
            
            isImmediateChild
        }.distinctBy { it.name }
        currentEntries = filtered.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(file.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (virtualPath.isEmpty()) "Root" else virtualPath,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (virtualPath.isEmpty()) onBack()
                        else virtualPath = virtualPath.substringBeforeLast("/", "")
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(currentEntries) { entry ->
                        ListItem(
                            headlineContent = { Text(entry.name) },
                            supportingContent = { 
                                if (!entry.isDirectory) Text(FileUtils.formatFileSize(entry.size))
                            },
                            leadingContent = {
                                Icon(
                                    if (entry.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                    contentDescription = null,
                                    tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            },
                            modifier = Modifier.clickable {
                                if (entry.isDirectory) {
                                    virtualPath = if (entry.fullPath.endsWith("/")) entry.fullPath.dropLast(1) else entry.fullPath
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
