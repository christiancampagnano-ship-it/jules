package com.example.ultraenhance.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ZeroDCEProcessor(private val context: Context) {

    private val tfLiteManager = TFLiteManager(context)
    private var interpreter: Interpreter? = null

    companion object {
        const val MODEL_FILE = "zero_dce_plus.tflite"
    }

    fun isModelAvailable(): Boolean {
        return tfLiteManager.isModelAvailable(MODEL_FILE)
    }

    private fun getInterpreter(): Interpreter? {
        if (interpreter == null && isModelAvailable()) {
            val buffer = tfLiteManager.loadModelFile(MODEL_FILE)
            interpreter = tfLiteManager.createInterpreter(buffer)
        }
        return interpreter
    }

    fun process(inputBitmap: Bitmap): Bitmap {
        val interp = getInterpreter() ?: return applyNativeFallback(inputBitmap)

        return try {
            val width = inputBitmap.width
            val height = inputBitmap.height

            val inputTensor = interp.getInputTensor(0)
            val inputShape = inputTensor.shape()

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
            outputBitmap
        } catch (e: Exception) {
            applyNativeFallback(inputBitmap)
        }
    }

    private fun applyNativeFallback(inputBitmap: Bitmap): Bitmap {
        val width = inputBitmap.width
        val height = inputBitmap.height
        val outputBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)

        // Adjust contrast and brightness to lift shadows and recover low-light details natively
        val brightness = 30f // Lift shadows/dark regions
        val contrast = 1.2f   // Moderate contrast boost

        val scale = contrast
        val translate = (-0.5f * contrast + 0.5f) * 255f + brightness

        val cm = ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )

        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(cm)
            isAntiAlias = true
            isFilterBitmap = true
        }

        canvas.drawBitmap(inputBitmap, 0f, 0f, paint)
        return outputBitmap
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
