package com.wise.file_manager

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wise.file_manager.ui.theme.WiseFileManagerTheme

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        requestStoragePermissions()

        setContent {
            WiseFileManagerTheme {
                val viewModel: FileViewModel = viewModel()
                val currentScreen by viewModel.currentScreen.collectAsState()

                Scaffold(
                    bottomBar = {
                        if (currentScreen == AppScreen.Home) {
                            NavigationBar {
                                NavigationBarItem(
                                    icon = { Icon(Icons.Default.CleaningServices, contentDescription = "Clean") },
                                    label = { Text("Clean") },
                                    selected = false,
                                    onClick = { /* TODO */ }
                                )
                                NavigationBarItem(
                                    icon = { Icon(Icons.Default.Folder, contentDescription = "Browse") },
                                    label = { Text("Browse") },
                                    selected = true,
                                    onClick = { viewModel.navigateToHome() }
                                )
                                NavigationBarItem(
                                    icon = { Icon(Icons.Default.Share, contentDescription = "Share") },
                                    label = { Text("Share") },
                                    selected = false,
                                    onClick = { /* TODO */ }
                                )
                            }
                        }
                    }
                ) { paddingValues ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        when (currentScreen) {
                            AppScreen.Home -> BrowseScreen(
                                onCategoryClick = { category ->
                                    viewModel.navigateToExplorer()
                                },
                                onStorageClick = {
                                    viewModel.navigateToExplorer()
                                }
                            )
                            AppScreen.Explorer -> FileManagerScreen(viewModel)
                        }
                    }
                }
            }
        }
    }

    private fun requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.addCategory("android.intent.category.DEFAULT")
                    intent.data = Uri.parse(String.format("package:%s", applicationContext.packageName))
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent()
                    intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                    startActivity(intent)
                }
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
            }
        }
    }
}
