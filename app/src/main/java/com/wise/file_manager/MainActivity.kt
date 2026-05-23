package com.wise.file_manager

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wise.file_manager.ui.theme.WiseFileManagerTheme
import java.io.File

import androidx.activity.enableEdgeToEdge

class MainActivity : FragmentActivity() {
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        requestStoragePermissions()

        setContent {
            WiseFileManagerTheme {
                val viewModel: FileViewModel = viewModel()
                val currentScreen by viewModel.currentScreen.collectAsState()
                val previewFile by viewModel.previewFile.collectAsState()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            val animationSpec = tween<Float>(durationMillis = 400, easing = FastOutSlowInEasing)
                            val slideSpec = tween<IntOffset>(durationMillis = 400, easing = FastOutSlowInEasing)
                            
                            if (targetState != AppScreen.Explorer) {
                                (slideInHorizontally(animationSpec = slideSpec) { it } + 
                                 fadeIn(animationSpec = animationSpec) + 
                                 scaleIn(initialScale = 0.92f, animationSpec = animationSpec))
                                    .togetherWith(
                                        slideOutHorizontally(animationSpec = slideSpec) { -it / 3 } + 
                                        fadeOut(animationSpec = animationSpec)
                                    )
                            } else {
                                (slideInHorizontally(animationSpec = slideSpec) { -it / 3 } + 
                                 fadeIn(animationSpec = animationSpec))
                                    .togetherWith(
                                        slideOutHorizontally(animationSpec = slideSpec) { it } + 
                                        fadeOut(animationSpec = animationSpec) + 
                                        scaleOut(targetScale = 0.92f, animationSpec = animationSpec)
                                    )
                            }.using(
                                SizeTransform(clip = false)
                            )
                        },
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                        label = "screen_transition"
                    ) { screen ->
                        ScreenWrapper(screen, viewModel, previewFile)
                    }
                }
            }
        }
    }

    @Composable
    private fun ScreenWrapper(screen: AppScreen, viewModel: FileViewModel, previewFile: File?) {
        Box(modifier = Modifier.fillMaxSize().graphicsLayer {
            clip = false
        }) {
            when (screen) {
                AppScreen.Home -> HomeScreen(viewModel)
                AppScreen.Explorer -> {
                    BackHandler {
                        if (!viewModel.goBack()) {
                            viewModel.navigateToScreen(AppScreen.Home)
                        }
                    }
                    FileManagerScreen(viewModel)
                }
                AppScreen.PdfViewer -> {
                    BackHandler { viewModel.closePreview() }
                    previewFile?.let { file ->
                        PdfViewerPage(
                            viewModel = viewModel,
                            file = file,
                            onBack = { viewModel.closePreview() }
                        )
                    }
                }
                AppScreen.AudioPlayer -> {
                    BackHandler { viewModel.closePreview() }
                    previewFile?.let { file ->
                        AudioPlayerScreen(
                            viewModel = viewModel,
                            file = file,
                            onBack = { viewModel.closePreview() }
                        )
                    }
                }
                AppScreen.ImageViewer -> {
                    BackHandler { viewModel.closePreview() }
                    previewFile?.let { file ->
                        ImageViewerScreen(
                            viewModel = viewModel,
                            file = file,
                            onBack = { viewModel.closePreview() }
                        )
                    }
                }
                AppScreen.VideoPlayer -> {
                    BackHandler { viewModel.closePreview() }
                    previewFile?.let { file ->
                        @OptIn(ExperimentalMaterial3Api::class)
                        VideoPlayerScreen(
                            viewModel = viewModel,
                            file = file,
                            onBack = { viewModel.closePreview() }
                        )
                    }
                }
                AppScreen.TextViewer -> {
                    BackHandler { viewModel.closePreview() }
                    previewFile?.let { file ->
                        TextViewerScreen(
                            viewModel = viewModel,
                            file = file,
                            onBack = { viewModel.closePreview() }
                        )
                    }
                }
                AppScreen.ArchiveExplorer -> {
                    BackHandler { viewModel.closePreview() }
                    previewFile?.let { file ->
                        ArchiveExplorerScreen(
                            viewModel = viewModel,
                            file = file,
                            onBack = { viewModel.closePreview() }
                        )
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
