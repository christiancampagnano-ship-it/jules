package com.example.ultraenhance.data

import android.content.Context
import android.content.res.AssetFileDescriptor
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class TFLiteManager(private val context: Context) {

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
            throw FileNotFoundException(
                "Model file '$modelName' not found in assets. " +
                        "Please place '$modelName' inside 'app/src/main/assets/'."
            )
        }
    }

    fun createInterpreter(modelBuffer: MappedByteBuffer): Interpreter {
        val options = Interpreter.Options()
        var gpuDelegate: GpuDelegate? = null

        try {
            gpuDelegate = GpuDelegate()
            options.addDelegate(gpuDelegate)
        } catch (e: Exception) {
            options.setNumThreads(4)
        }

        return try {
            Interpreter(modelBuffer, options)
        } catch (e: Exception) {
            // Fallback to 4 CPU threads if GPU interpreter creation fails
            gpuDelegate?.close()
            val fallbackOptions = Interpreter.Options().apply {
                setNumThreads(4)
            }
            Interpreter(modelBuffer, fallbackOptions)
        }
    }
}
