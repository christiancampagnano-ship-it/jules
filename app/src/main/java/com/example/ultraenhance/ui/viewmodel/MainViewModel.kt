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

sealed class UiState {
    object Idle : UiState()
    object Processing : UiState()
    data class Success(val original: Bitmap, val enhanced: Bitmap, val isFallback: Boolean = false) : UiState()
    data class Error(val message: String) : UiState()
}

class MainViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun processImage(context: Context, inputBitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.Default) {
            _uiState.value = UiState.Processing
            try {
                val zeroDCEProcessor = ZeroDCEProcessor(context)
                val isZeroDCEAvailable = zeroDCEProcessor.isModelAvailable()
                val stage1Output = zeroDCEProcessor.process(inputBitmap)
                zeroDCEProcessor.close()

                val superResProcessor = SuperResProcessor(context)
                val isSuperResAvailable = superResProcessor.isModelAvailable()
                val stage2Output = superResProcessor.process(stage1Output)
                superResProcessor.close()

                val isFallbackUsed = !isZeroDCEAvailable || !isSuperResAvailable

                if (isFallbackUsed) {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(
                            context.applicationContext,
                            "Running in Native Fallback mode. Add .tflite models to assets for AI mode.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                _uiState.value = UiState.Success(
                    original = inputBitmap,
                    enhanced = stage2Output,
                    isFallback = isFallbackUsed
                )
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
