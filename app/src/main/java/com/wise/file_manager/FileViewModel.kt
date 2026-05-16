package com.wise.file_manager

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*

enum class AppScreen {
    Home, Explorer
}

class FileViewModel : ViewModel() {

    private val rootDirectory = Environment.getExternalStorageDirectory()
    
    private val _currentScreen = MutableStateFlow(AppScreen.Home)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _currentPath = MutableStateFlow(rootDirectory.absolutePath)
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val _files = MutableStateFlow<List<File>>(emptyList())
    val files: StateFlow<List<File>> = _files.asStateFlow()

    private val _breadcrumbs = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val breadcrumbs: StateFlow<List<Pair<String, String>>> = _breadcrumbs.asStateFlow()

    private val _previewFile = MutableStateFlow<File?>(null)
    val previewFile: StateFlow<File?> = _previewFile.asStateFlow()

    private val pathStack = Stack<String>()

    val storageLocations = listOf(
        "Internal Storage" to Environment.getExternalStorageDirectory().absolutePath,
        "Downloads" to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath,
        "DCIM" to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath,
        "Documents" to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath,
        "Music" to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath,
        "Pictures" to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath,
        "Movies" to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).absolutePath,
    )

    init {
        loadFiles(rootDirectory.absolutePath)
    }

    fun navigateToHome() {
        _currentScreen.value = AppScreen.Home
        pathStack.clear()
    }

    fun navigateToExplorer(path: String = rootDirectory.absolutePath) {
        _currentScreen.value = AppScreen.Explorer
        loadFiles(path)
    }

    fun openDirectory(file: File) {
        if (file.isDirectory) {
            pathStack.push(_currentPath.value)
            loadFiles(file.absolutePath)
        } else {
            _previewFile.value = file
        }
    }

    fun closePreview() {
        _previewFile.value = null
    }

    fun navigateToPath(path: String) {
        if (path != _currentPath.value) {
            pathStack.push(_currentPath.value)
            loadFiles(path)
        }
    }

    fun goBack(): Boolean {
        if (_currentScreen.value == AppScreen.Explorer) {
            if (pathStack.isNotEmpty()) {
                val previousPath = pathStack.pop()
                loadFiles(previousPath)
                return true
            } else {
                _currentScreen.value = AppScreen.Home
                return true
            }
        }
        return false
    }

    private fun loadFiles(path: String) {
        _currentPath.value = path
        updateBreadcrumbs(path)
        viewModelScope.launch {
            val fileList = withContext(Dispatchers.IO) {
                val directory = File(path)
                directory.listFiles()?.toList()?.sortedWith(
                    compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }
                ) ?: emptyList()
            }
            _files.value = fileList
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
