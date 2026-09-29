package com.example.ultraenhance.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ultraenhance.data.ImageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class EnhancementMode {
    FULL,           // Low-Light Recovery + Super Resolution
    LOW_LIGHT,      // Zero-DCE++ Low-Light Recovery only
    SUPER_RES       // FastSRGAN Super Resolution / Zoom Enhance only
}

sealed class UiState {
    object Idle : UiState()
    data class Processing(
        val message: String = "Processing image...",
        val progressPercent: Int = 0,
        val currentTile: Int = 0,
        val totalTiles: Int = 0
    ) : UiState()
    data class Success(
        val original: Bitmap,
        val enhanced: Bitmap,
        val isFallback: Boolean = false,
        val mode: EnhancementMode = EnhancementMode.FULL
    ) : UiState()
    data class Error(val message: String) : UiState()
}

class MainViewModel : ViewModel() {

    private val imageRepository = ImageRepository()

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _selectedMode = MutableStateFlow(EnhancementMode.FULL)
    val selectedMode: StateFlow<EnhancementMode> = _selectedMode.asStateFlow()

    fun setEnhancementMode(mode: EnhancementMode) {
        _selectedMode.value = mode
    }

    fun executeUltraEnhance(context: Context, inputBitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.value = UiState.Processing("Initializing 3-Stage AI Pipeline...", 5)
            try {
                withContext(Dispatchers.Default) {
                    val (enhancedBitmap, isFallbackUsed) = imageRepository.executeUnifiedPipeline(
                        context = context,
                        inputBitmap = inputBitmap
                    ) { stageMessage, progressPercent ->
                        _uiState.value = UiState.Processing(
                            message = stageMessage,
                            progressPercent = progressPercent
                        )
                    }

                    if (isFallbackUsed) {
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(
                                context.applicationContext,
                                "Running in Native Fallback Mode. Add .tflite / .onnx files to assets for AI enhancement.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }

                    _uiState.value = UiState.Success(
                        original = inputBitmap,
                        enhanced = enhancedBitmap,
                        isFallback = isFallbackUsed,
                        mode = EnhancementMode.FULL
                    )
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error(
                    message = e.localizedMessage ?: e.message ?: "An unknown error occurred during processing."
                )
            }
        }
    }

    fun processImage(context: Context, inputBitmap: Bitmap, mode: EnhancementMode = _selectedMode.value) {
        if (mode == EnhancementMode.FULL) {
            executeUltraEnhance(context, inputBitmap)
            return
        }

        viewModelScope.launch {
            _uiState.value = UiState.Processing("Initializing pipeline...", 5)
            try {
                withContext(Dispatchers.Default) {
                    val (enhancedBitmap, isFallbackUsed) = imageRepository.processImagePipeline(
                        context = context,
                        inputBitmap = inputBitmap,
                        mode = mode
                    ) { currentTile, totalTiles ->
                        val percent = if (totalTiles > 0) ((currentTile.toFloat() / totalTiles.toFloat()) * 100).toInt() else 50
                        _uiState.value = UiState.Processing(
                            message = "Super-resolving tile $currentTile of $totalTiles...",
                            progressPercent = percent,
                            currentTile = currentTile,
                            totalTiles = totalTiles
                        )
                    }

                    if (isFallbackUsed) {
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(
                                context.applicationContext,
                                "Running in Native Fallback Mode. Add .tflite / .onnx files to assets for AI enhancement.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }

                    _uiState.value = UiState.Success(
                        original = inputBitmap,
                        enhanced = enhancedBitmap,
                        isFallback = isFallbackUsed,
                        mode = mode
                    )
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error(
                    message = e.localizedMessage ?: e.message ?: "An unknown error occurred during processing."
                )
            }
        }
    }

    fun resetState() {
        _uiState.value = UiState.Idle
    }
}
