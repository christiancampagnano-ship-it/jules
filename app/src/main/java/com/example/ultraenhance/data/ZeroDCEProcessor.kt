package com.example.ultraenhance.data

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ZeroDCEProcessor(private val context: Context) {

    private val tfLiteManager = TFLiteManager(context)
    private var interpreter: Interpreter? = null

    companion object {
        const val MODEL_FILE = "zero_dce_plus.tflite"
    }

    private fun getInterpreter(): Interpreter {
        if (interpreter == null) {
            val buffer = tfLiteManager.loadModelFile(MODEL_FILE)
            interpreter = tfLiteManager.createInterpreter(buffer)
        }
        return interpreter!!
    }

    fun process(inputBitmap: Bitmap): Bitmap {
        val interp = getInterpreter()

        val width = inputBitmap.width
        val height = inputBitmap.height

        val inputTensor = interp.getInputTensor(0)
        val inputShape = inputTensor.shape() // Expected shape e.g. [1, height, width, 3] or dynamic

        // Check if input needs to be resized if tensor shape is fixed and doesn't match
        if (inputShape.size == 4 && inputShape[1] > 0 && inputShape[2] > 0) {
            val reqHeight = inputShape[1]
            val reqWidth = inputShape[2]
            if (reqHeight != height || reqWidth != width) {
                interp.resizeInput(0, intArrayOf(1, height, width, 3))
                interp.allocateTensors()
            }
        }

        val inputBuffer = ByteBuffer.allocateDirect(1 * height * width * 3 * 4)
        inputBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(width * height)
        inputBitmap.getPixels(intValues, 0, width, 0, 0, width, height)

        var pixelIndex = 0
        for (i in 0 until height) {
            for (j in 0 until width) {
                val pixel = intValues[pixelIndex++]
                val r = ((pixel shr 16) and 0xFF) / 255.0f
                val g = ((pixel shr 8) and 0xFF) / 255.0f
                val b = (pixel and 0xFF) / 255.0f

                inputBuffer.putFloat(r)
                inputBuffer.putFloat(g)
                inputBuffer.putFloat(b)
            }
        }

        val outputBuffer = ByteBuffer.allocateDirect(1 * height * width * 3 * 4)
        outputBuffer.order(ByteOrder.nativeOrder())

        interp.run(inputBuffer, outputBuffer)

        outputBuffer.rewind()

        val outputBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val outPixels = IntArray(width * height)

        for (i in 0 until width * height) {
            val r = (outputBuffer.float.coerceIn(0.0f, 1.0f) * 255.0f).toInt()
            val g = (outputBuffer.float.coerceIn(0.0f, 1.0f) * 255.0f).toInt()
            val b = (outputBuffer.float.coerceIn(0.0f, 1.0f) * 255.0f).toInt()

            outPixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        outputBitmap.setPixels(outPixels, 0, width, 0, 0, width, height)
        return outputBitmap
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
