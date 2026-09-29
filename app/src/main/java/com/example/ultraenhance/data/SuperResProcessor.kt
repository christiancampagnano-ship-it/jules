package com.example.ultraenhance.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SuperResProcessor(private val context: Context) {

    private val tfLiteManager = TFLiteManager(context)
    private var interpreter: Interpreter? = null

    companion object {
        const val MODEL_FILE = "fast_srgan.tflite"
        const val SCALE_FACTOR = 4
        const val TILE_SIZE = 512
        const val OVERLAP = 16
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

    fun process(
        inputBitmap: Bitmap,
        onProgress: ((currentTile: Int, totalTiles: Int) -> Unit)? = null
    ): Bitmap {
        val interp = getInterpreter() ?: return applyNativeFallback(inputBitmap)

        return try {
            val inWidth = inputBitmap.width
            val inHeight = inputBitmap.height

            val outWidth = inWidth * SCALE_FACTOR
            val outHeight = inHeight * SCALE_FACTOR

            val outputBitmap = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(outputBitmap)

            val paint = Paint().apply {
                isAntiAlias = true
                isFilterBitmap = true
            }

            // Calculate grid tiles with 16px overlap
            val step = TILE_SIZE - (OVERLAP * 2)

            val xTiles = Math.ceil(inWidth.toDouble() / step).toInt()
            val yTiles = Math.ceil(inHeight.toDouble() / step).toInt()
            val totalTiles = xTiles * yTiles

            var tileCount = 0

            for (ty in 0 until yTiles) {
                for (tx in 0 until xTiles) {
                    tileCount++
                    onProgress?.invoke(tileCount, totalTiles)

                    val startX = (tx * step).coerceIn(0, inWidth - 1)
                    val startY = (ty * step).coerceIn(0, inHeight - 1)

                    val endX = (startX + TILE_SIZE).coerceAtMost(inWidth)
                    val endY = (startY + TILE_SIZE).coerceAtMost(inHeight)

                    val tileW = endX - startX
                    val tileH = endY - startY

                    if (tileW <= 0 || tileH <= 0) continue

                    val tileBitmap = Bitmap.createBitmap(inputBitmap, startX, startY, tileW, tileH)
                    val processedTile = processTile(interp, tileBitmap)
                    tileBitmap.recycle()

                    // Crop out the overlap regions from the processed tile output to eliminate seam lines
                    val cropLeft = if (startX > 0) OVERLAP * SCALE_FACTOR else 0
                    val cropTop = if (startY > 0) OVERLAP * SCALE_FACTOR else 0
                    val cropRight = if (endX < inWidth) OVERLAP * SCALE_FACTOR else 0
                    val cropBottom = if (endY < inHeight) OVERLAP * SCALE_FACTOR else 0

                    val validTileW = processedTile.width - cropLeft - cropRight
                    val validTileH = processedTile.height - cropTop - cropBottom

                    if (validTileW > 0 && validTileH > 0) {
                        val srcRect = Rect(
                            cropLeft,
                            cropTop,
                            cropLeft + validTileW,
                            cropTop + validTileH
                        )

                        val dstLeft = (startX * SCALE_FACTOR) + cropLeft
                        val dstTop = (startY * SCALE_FACTOR) + cropTop
                        val dstRect = Rect(
                            dstLeft,
                            dstTop,
                            dstLeft + validTileW,
                            dstTop + validTileH
                        )

                        canvas.drawBitmap(processedTile, srcRect, dstRect, paint)
                    }

                    processedTile.recycle()
                }
            }

            applyUnsharpMask(outputBitmap)
        } catch (e: Exception) {
            applyNativeFallback(inputBitmap)
        }
    }

    private fun processTile(interp: Interpreter, tileBitmap: Bitmap): Bitmap {
        val tileW = tileBitmap.width
        val tileH = tileBitmap.height

        val inputTensor = interp.getInputTensor(0)
        val inputShape = inputTensor.shape()

        if (inputShape.size == 4 && inputShape[1] > 0 && inputShape[2] > 0) {
            val reqHeight = inputShape[1]
            val reqWidth = inputShape[2]
            if (reqHeight != tileH || reqWidth != tileW) {
                interp.resizeInput(0, intArrayOf(1, tileH, tileW, 3))
                interp.allocateTensors()
            }
        }

        val outTileW = tileW * SCALE_FACTOR
        val outTileH = tileH * SCALE_FACTOR

        val inputBuffer = ByteBuffer.allocateDirect(1 * tileH * tileW * 3 * 4)
        inputBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(tileW * tileH)
        tileBitmap.getPixels(intValues, 0, tileW, 0, 0, tileW, tileH)

        var pixelIndex = 0
        for (i in 0 until tileH) {
            for (j in 0 until tileW) {
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

        val outputBuffer = ByteBuffer.allocateDirect(1 * outTileH * outTileW * 3 * 4)
        outputBuffer.order(ByteOrder.nativeOrder())

        interp.run(inputBuffer, outputBuffer)
        outputBuffer.rewind()

        val tileResult = Bitmap.createBitmap(outTileW, outTileH, Bitmap.Config.ARGB_8888)
        val outPixels = IntArray(outTileW * outTileH)

        for (i in 0 until outTileW * outTileH) {
            val r = (outputBuffer.float.coerceIn(0.0f, 1.0f) * 255.0f).toInt()
            val g = (outputBuffer.float.coerceIn(0.0f, 1.0f) * 255.0f).toInt()
            val b = (outputBuffer.float.coerceIn(0.0f, 1.0f) * 255.0f).toInt()

            outPixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        tileResult.setPixels(outPixels, 0, outTileW, 0, 0, outTileW, outTileH)
        return tileResult
    }

    private fun applyUnsharpMask(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val resultPixels = pixels.clone()

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x

                val top = pixels[(y - 1) * width + x]
                val bottom = pixels[(y + 1) * width + x]
                val left = pixels[y * width + (x - 1)]
                val right = pixels[y * width + (x + 1)]
                val center = pixels[idx]

                val r = (5 * ((center shr 16) and 0xFF) -
                        ((top shr 16) and 0xFF) -
                        ((bottom shr 16) and 0xFF) -
                        ((left shr 16) and 0xFF) -
                        ((right shr 16) and 0xFF)).coerceIn(0, 255)

                val g = (5 * ((center shr 8) and 0xFF) -
                        ((top shr 8) and 0xFF) -
                        ((bottom shr 8) and 0xFF) -
                        ((left shr 8) and 0xFF) -
                        ((right shr 8) and 0xFF)).coerceIn(0, 255)

                val b = (5 * (center and 0xFF) -
                        (top and 0xFF) -
                        (bottom and 0xFF) -
                        (left and 0xFF) -
                        (right and 0xFF)).coerceIn(0, 255)

                resultPixels[idx] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        bitmap.setPixels(resultPixels, 0, width, 0, 0, width, height)
        return bitmap
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

        return applyUnsharpMask(scaledBitmap)
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
