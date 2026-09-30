package com.example.ultraenhance.data

import android.content.Context
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import java.io.File
import java.io.FileOutputStream

class ONNXManager(private val context: Context) {

    private var ortEnv: OrtEnvironment? = null

    companion object {
        private const val TAG = "UltraEnhance"
    }

    fun getOrtEnvironment(): OrtEnvironment {
        if (ortEnv == null) {
            ortEnv = OrtEnvironment.getEnvironment()
        }
        return ortEnv!!
    }

    fun isModelAvailable(modelName: String): Boolean {
        return try {
            val inputStream = context.assets.open(modelName)
            val size = inputStream.available()
            inputStream.close()
            size > 0
        } catch (e: Exception) {
            false
        }
    }

    fun createSession(modelName: String): OrtSession? {
        if (!isModelAvailable(modelName)) {
            Log.w(TAG, "ONNX model '$modelName' not found or empty in assets.")
            return null
        }

        val env = getOrtEnvironment()
        val modelFile = getFileFromAssets(modelName) ?: return null

        val options = OrtSession.SessionOptions()
        var activeProvider = "CPU"

        try {
            options.addNnapi()
            activeProvider = "NNAPI / Qualcomm QNN NPU"
            Log.i(TAG, "Active Provider for $modelName: $activeProvider")
        } catch (e: Exception) {
            Log.e(TAG, "Acceleration setup failed for $modelName, falling back to CPU", e)
            try {
                options.setInterOpNumThreads(4)
                options.setIntraOpNumThreads(4)
            } catch (_: Exception) {}
        }

        return try {
            val session = env.createSession(modelFile.absolutePath, options)
            Log.i(TAG, "OrtSession created successfully for $modelName with provider: $activeProvider")
            session
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create OrtSession with accelerated options for $modelName", e)
            try {
                val fallbackSession = env.createSession(modelFile.absolutePath, OrtSession.SessionOptions())
                Log.i(TAG, "Fallback OrtSession created for $modelName (CPU Default)")
                fallbackSession
            } catch (e2: Exception) {
                Log.e(TAG, "Critical: Could not create OrtSession for $modelName", e2)
                null
            }
        }
    }

    private fun getFileFromAssets(modelName: String): File? {
        return try {
            val cacheFile = File(context.cacheDir, modelName)
            if (!cacheFile.exists()) {
                context.assets.open(modelName).use { inputStream ->
                    FileOutputStream(cacheFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
            cacheFile
        } catch (e: Exception) {
            Log.e(TAG, "Error copying $modelName from assets to cacheDir", e)
            null
        }
    }

    fun close() {
        try {
            ortEnv?.close()
            ortEnv = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing OrtEnvironment", e)
        }
    }
}
