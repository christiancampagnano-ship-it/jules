package com.example.ultraenhance.data

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class TFLiteManager(private val context: Context) {

    companion object {
        private const val TAG = "UltraEnhance"
    }

    fun isModelAvailable(modelName: String): Boolean {
        return try {
            val fileDescriptor = context.assets.openFd(modelName)
            val length = fileDescriptor.length
            fileDescriptor.close()
            length > 0
        } catch (e: Exception) {
            false
        }
    }

    fun loadModelFile(modelName: String): MappedByteBuffer {
        return try {
            val fileDescriptor: AssetFileDescriptor = context.assets.openFd(modelName)
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel: FileChannel = inputStream.channel
            val startOffset: Long = fileDescriptor.startOffset
            val declaredLength: Long = fileDescriptor.declaredLength
            val buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            fileDescriptor.close()
            buffer
        } catch (e: IOException) {
            Log.e(TAG, "Model file '$modelName' not found or failed to load from assets.", e)
            throw FileNotFoundException(
                "Model file '$modelName' not found in assets. " +
                        "Please place '$modelName' inside 'app/src/main/assets/'."
            )
        }
    }

    fun createInterpreter(modelBuffer: MappedByteBuffer): Interpreter {
        val interpreterOptions = Interpreter.Options()
        var gpuDelegate: GpuDelegate? = null
        var activeDelegate = "CPU (4 threads)"

        try {
            val compatList = CompatibilityList()
            if (compatList.isDelegateSupportedOnThisDevice) {
                val delegateOptions = compatList.bestOptionsForThisDevice
                gpuDelegate = GpuDelegate(delegateOptions)
                interpreterOptions.addDelegate(gpuDelegate)
                activeDelegate = "GpuDelegate"
                Log.i(TAG, "Active TFLite Delegate: GpuDelegate initialized successfully.")
            } else {
                interpreterOptions.setNumThreads(4)
                Log.i(TAG, "GpuDelegate not supported on device; using 4 CPU threads.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing GpuDelegate, falling back to CPU", e)
            interpreterOptions.setNumThreads(4)
        }

        return try {
            val interpreter = Interpreter(modelBuffer, interpreterOptions)
            Log.i(TAG, "Interpreter created successfully with $activeDelegate")
            interpreter
        } catch (e: Exception) {
            Log.e(TAG, "Failed creating Interpreter with $activeDelegate, trying CPU fallback", e)
            gpuDelegate?.close()
            val fallbackOptions = Interpreter.Options().apply {
                setNumThreads(4)
            }
            Interpreter(modelBuffer, fallbackOptions)
        }
    }
}
