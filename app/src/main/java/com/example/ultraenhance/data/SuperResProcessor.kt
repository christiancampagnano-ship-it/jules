package com.example.ultraenhance.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SuperResProcessor(private val context: Context) {

    private val tfLiteManager = TFLiteManager(context)
    private var interpreter: Interpreter? = null

    companion object {
        const val MODEL_FILE = "fast_srgan.tflite"
        const val SCALE_FACTOR = 2
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
            val inWidth = inputBitmap.width
            val inHeight = inputBitmap.height

            val inputTensor = interp.getInputTensor(0)
            val inputShape = inputTensor.shape()

            if (inputShape.size == 4 && inputShape[1] > 0 && inputShape[2] > 0) {
                val reqHeight = inputShape[1]
                val reqWidth = inputShape[2]
                if (reqHeight != inHeight || reqWidth != inWidth) {
                    interp.resizeInput(0, intArrayOf(1, inHeight, inWidth, 3))
                    interp.allocateTensors()
                }
            }

            val outTensor = interp.getOutputTensor(0)
            val outShape = outTensor.shape()

            val outHeight: Int
            val outWidth: Int
            if (outShape.size == 4 && outShape[1] > 0 && outShape[2] > 0) {
                outHeight = outShape[1]
                outWidth = outShape[2]
            } else {
                outHeight = inHeight * SCALE_FACTOR
                outWidth = inWidth * SCALE_FACTOR
            }

            val inputBuffer = ByteBuffer.allocateDirect(1 * inHeight * inWidth * 3 * 4)
            inputBuffer.order(ByteOrder.nativeOrder())

            val intValues = IntArray(inWidth * inHeight)
            inputBitmap.getPixels(intValues, 0, inWidth, 0, 0, inWidth, inHeight)

            var pixelIndex = 0
            for (i in 0 until inHeight) {
                for (j in 0 until inWidth) {
                    val pixel = intValues[pixelIndex++]
                    val r = ((pixel shr 16) and 0xFF) / 255.0f
                    val g = ((pixel shr 8) and 0xFF) / 255.0f
                    val b = (pixel and 0xFF) / 255.0f

                    inputBuffer.putFloat(r)
                    inputBuffer.putFloat(g)
                    inputBuffer.putFloat(b)
                }
            }
            inputBuffer.rewind()

            val outputBuffer = ByteBuffer.allocateDirect(1 * outHeight * outWidth * 3 * 4)
            outputBuffer.order(ByteOrder.nativeOrder())

            interp.run(inputBuffer, outputBuffer)

            outputBuffer.rewind()

            val outputBitmap = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
            val outPixels = IntArray(outWidth * outHeight)

            for (i in 0 until outWidth * outHeight) {
                val r = (outputBuffer.float.coerceIn(0.0f, 1.0f) * 255.0f).toInt()
                val g = (outputBuffer.float.coerceIn(0.0f, 1.0f) * 255.0f).toInt()
                val b = (outputBuffer.float.coerceIn(0.0f, 1.0f) * 255.0f).toInt()

                outPixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }

            outputBitmap.setPixels(outPixels, 0, outWidth, 0, 0, outWidth, outHeight)
            outputBitmap
        } catch (e: Exception) {
            applyNativeFallback(inputBitmap)
        }
    }

    private fun applyNativeFallback(inputBitmap: Bitmap): Bitmap {
        val outWidth = inputBitmap.width * SCALE_FACTOR
        val outHeight = inputBitmap.height * SCALE_FACTOR

        val scaledBitmap = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(scaledBitmap)

        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
        }

        val destRect = RectF(0f, 0f, outWidth.toFloat(), outHeight.toFloat())
        canvas.drawBitmap(inputBitmap, null, destRect, paint)

        return scaledBitmap
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
