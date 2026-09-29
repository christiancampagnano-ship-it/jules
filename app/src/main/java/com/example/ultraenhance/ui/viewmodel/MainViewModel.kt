package com.example.ultraenhance.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ultraenhance.data.SuperResProcessor
import com.example.ultraenhance.data.ZeroDCEProcessor
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

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _selectedMode = MutableStateFlow(EnhancementMode.FULL)
    val selectedMode: StateFlow<EnhancementMode> = _selectedMode.asStateFlow()

    fun setEnhancementMode(mode: EnhancementMode) {
        _selectedMode.value = mode
    }

    fun processImage(context: Context, inputBitmap: Bitmap, mode: EnhancementMode = _selectedMode.value) {
        viewModelScope.launch {
            _uiState.value = UiState.Processing("Initializing pipeline...")
            try {
                withContext(Dispatchers.Default) {
                    var currentBitmap = inputBitmap
                    var isFallbackUsed = false

                    when (mode) {
                        EnhancementMode.FULL -> {
                            _uiState.value = UiState.Processing("Applying Stage 1: Zero-DCE++ Low-Light Recovery...")
                            val zeroDCEProcessor = ZeroDCEProcessor(context)
                            val isZeroDCEAvailable = zeroDCEProcessor.isModelAvailable()
                            val stage1Output = zeroDCEProcessor.process(currentBitmap)
                            zeroDCEProcessor.close()

                            _uiState.value = UiState.Processing("Applying Stage 2: Tiled FastSRGAN Super Resolution...")
                            val superResProcessor = SuperResProcessor(context)
                            val isSuperResAvailable = superResProcessor.isModelAvailable()
                            val stage2Output = superResProcessor.process(stage1Output) { currentTile, totalTiles ->
                                _uiState.value = UiState.Processing(
                                    message = "Super-resolving tile $currentTile of $totalTiles...",
                                    currentTile = currentTile,
                                    totalTiles = totalTiles
                                )
                            }
                            superResProcessor.close()

                            currentBitmap = stage2Output
                            isFallbackUsed = !isZeroDCEAvailable || !isSuperResAvailable
                        }
                        EnhancementMode.LOW_LIGHT -> {
                            _uiState.value = UiState.Processing("Applying Zero-DCE++ Low-Light Recovery...")
                            val zeroDCEProcessor = ZeroDCEProcessor(context)
                            val isZeroDCEAvailable = zeroDCEProcessor.isModelAvailable()
                            val stage1Output = zeroDCEProcessor.process(currentBitmap)
                            zeroDCEProcessor.close()

                            currentBitmap = stage1Output
                            isFallbackUsed = !isZeroDCEAvailable
                        }
                        EnhancementMode.SUPER_RES -> {
                            _uiState.value = UiState.Processing("Applying Tiled FastSRGAN Super Resolution...")
                            val superResProcessor = SuperResProcessor(context)
                            val isSuperResAvailable = superResProcessor.isModelAvailable()
                            val stage2Output = superResProcessor.process(currentBitmap) { currentTile, totalTiles ->
                                _uiState.value = UiState.Processing(
                                    message = "Super-resolving tile $currentTile of $totalTiles...",
                                    currentTile = currentTile,
                                    totalTiles = totalTiles
                                )
                            }
                            superResProcessor.close()

                            currentBitmap = stage2Output
                            isFallbackUsed = !isSuperResAvailable
                        }
                    }

                    if (isFallbackUsed) {
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(
                                context.applicationContext,
                                "Running in Native Fallback Mode. Add .tflite files to assets for AI enhancement.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }

                    _uiState.value = UiState.Success(
                        original = inputBitmap,
                        enhanced = currentBitmap,
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
