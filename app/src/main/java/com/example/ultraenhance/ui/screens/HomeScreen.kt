package com.example.ultraenhance.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.ultraenhance.data.ImageRepository
import com.example.ultraenhance.ui.components.BeforeAfterSlider
import com.example.ultraenhance.ui.components.SideBySideViewer
import com.example.ultraenhance.ui.viewmodel.EnhancementMode
import com.example.ultraenhance.ui.viewmodel.MainViewModel
import com.example.ultraenhance.ui.viewmodel.UiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    windowSizeClass: WindowSizeClass,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val selectedMode by viewModel.selectedMode.collectAsState()
    val imageRepository = remember { ImageRepository() }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val bitmap = imageRepository.loadBitmapFromUri(context, uri)
            if (bitmap != null) {
                viewModel.processImage(context, bitmap)
            } else {
                Toast.makeText(context, "Failed to load selected image.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("UltraEnhance AI Pipeline") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Mode Selector Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = selectedMode == EnhancementMode.FULL,
                    onClick = { viewModel.setEnhancementMode(EnhancementMode.FULL) },
                    label = { Text("Full AI Pipeline") }
                )
                FilterChip(
                    selected = selectedMode == EnhancementMode.LOW_LIGHT,
                    onClick = { viewModel.setEnhancementMode(EnhancementMode.LOW_LIGHT) },
                    label = { Text("Low Light Only") }
                )
                FilterChip(
                    selected = selectedMode == EnhancementMode.SUPER_RES,
                    onClick = { viewModel.setEnhancementMode(EnhancementMode.SUPER_RES) },
                    label = { Text("Super Res / Zoom") }
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when (val state = uiState) {
                    is UiState.Idle -> {
                        Text(
                            text = "Select a photo to enhance",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is UiState.Processing -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            if (state.totalTiles > 0) {
                                val progress = state.currentTile.toFloat() / state.totalTiles.toFloat()
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth(0.8f)
                                        .height(8.dp)
                                )
                            } else {
                                CircularProgressIndicator()
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    is UiState.Success -> {
                        val isCompact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact
                        if (isCompact) {
                            BeforeAfterSlider(
                                original = state.original,
                                enhanced = state.enhanced,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            SideBySideViewer(
                                original = state.original,
                                enhanced = state.enhanced,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    is UiState.Error -> {
                        Text(
                            text = "Error: ${state.message}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            // Controls Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                ) {
                    Text("Select Photo")
                }

                if (uiState is UiState.Success) {
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = {
                            val successState = uiState as UiState.Success
                            val savedUri = imageRepository.saveImageToGallery(context, successState.enhanced)
                            if (savedUri != null) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Enhanced photo saved to Gallery!")
                                }
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Failed to save image to Gallery.")
                                }
                            }
                        }
                    ) {
                        Text("Save to Gallery")
                    }
                }
            }
        }
    }
}
