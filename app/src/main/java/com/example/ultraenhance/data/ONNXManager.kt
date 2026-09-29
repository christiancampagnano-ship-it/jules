package com.example.ultraenhance.data

import android.content.Context
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class ONNXManager(private val context: Context) {

    private var ortEnv: OrtEnvironment? = null

    fun getOrtEnvironment(): OrtEnvironment {
        if (ortEnv == null) {
            ortEnv = OrtEnvironment.getEnvironment()
        }
        return ortEnv!!
    }

    fun isModelAvailable(modelName: String): Boolean {
        return try {
            val inputStream = context.assets.open(modelName)
            inputStream.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun createSession(modelName: String): OrtSession? {
        if (!isModelAvailable(modelName)) return null

        val env = getOrtEnvironment()
        val modelFile = getFileFromAssets(modelName) ?: return null

        val options = OrtSession.SessionOptions()
        try {
            // Attempt NNAPI / Qualcomm NPU acceleration for Snapdragon devices
            options.addNnapi()
        } catch (e: Exception) {
            // Fallback to CPU multi-threading if NNAPI execution provider is unsupported
            try {
                options.setInterOpNumThreads(4)
                options.setIntraOpNumThreads(4)
            } catch (_: Exception) {}
        }

        return try {
            env.createSession(modelFile.absolutePath, options)
        } catch (e: Exception) {
            // Memory or execution provider fallback to default session options
            try {
                env.createSession(modelFile.absolutePath, OrtSession.SessionOptions())
            } catch (e2: Exception) {
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
            null
        }
    }

    fun close() {
        try {
            ortEnv?.close()
            ortEnv = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
