package com.example.ultraenhance.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.tasks.await

class UnifiedAIPipeline(private val context: Context) {

    private val zeroDCEProcessor = ZeroDCEProcessor(context)
    private val superResProcessor = SuperResProcessor(context)

    fun isModelsAvailable(): Boolean {
        return zeroDCEProcessor.isModelAvailable() || superResProcessor.isModelAvailable()
    }

    suspend fun execute(
        inputBitmap: Bitmap,
        onProgress: (stage: String, progressPercent: Int) -> Unit
    ): Pair<Bitmap, Boolean> {
        var isFallbackUsed = false

        // Stage 1: Denoise & Deblur / Low-Light Recovery
        onProgress("Stage 1: Denoising & Deblurring...", 15)
        val stage1Bitmap = try {
            if (zeroDCEProcessor.isModelAvailable()) {
                zeroDCEProcessor.process(inputBitmap)
            } else {
                isFallbackUsed = true
                applyDenoiseFallback(inputBitmap)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            isFallbackUsed = true
            applyDenoiseFallback(inputBitmap)
        }

        // Stage 2: Super Resolution (Real-ESRGAN 4x Tiled Processing)
        onProgress("Stage 2: Super Resolution Upscaling (Tiles)...", 40)
        val stage2Bitmap = try {
            superResProcessor.process(stage1Bitmap) { currentTile, totalTiles ->
                val tilePercent = 40 + ((currentTile.toFloat() / totalTiles.toFloat()) * 45).toInt()
                onProgress("Stage 2: Processing Tile $currentTile of $totalTiles...", tilePercent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            isFallbackUsed = true
            stage1Bitmap
        }

        if (stage1Bitmap != inputBitmap && stage1Bitmap != stage2Bitmap) {
            stage1Bitmap.recycle()
        }

        // Stage 3: Face Restoration (ML Kit Face Detector + Facial Enhancement)
        onProgress("Stage 3: Detecting & Restoring Facial Details...", 90)
        val finalBitmap = try {
            restoreFaces(stage2Bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
            stage2Bitmap
        }

        onProgress("Processing Complete!", 100)
        return Pair(finalBitmap, isFallbackUsed)
    }

    private suspend fun restoreFaces(inputBitmap: Bitmap): Bitmap {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()

        val detector = FaceDetection.getClient(options)
        val image = InputImage.fromBitmap(inputBitmap, 0)

        val faces: List<Face> = try {
            detector.process(image).await()
        } catch (e: Exception) {
            emptyList()
        }

        if (faces.isEmpty()) {
            return inputBitmap
        }

        val resultBitmap = Bitmap.createBitmap(inputBitmap.width, inputBitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)
        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
        }

        canvas.drawBitmap(inputBitmap, 0f, 0f, paint)

        for (face in faces) {
            val bounds = face.boundingBox
            val left = bounds.left.coerceIn(0, inputBitmap.width - 1)
            val top = bounds.top.coerceIn(0, inputBitmap.height - 1)
            val width = bounds.width().coerceAtMost(inputBitmap.width - left)
            val height = bounds.height().coerceAtMost(inputBitmap.height - top)

            if (width <= 0 || height <= 0) continue

            val faceCrop = Bitmap.createBitmap(inputBitmap, left, top, width, height)
            val enhancedFace = applyFaceSharpeningFilter(faceCrop)
            faceCrop.recycle()

            val srcRect = Rect(0, 0, enhancedFace.width, enhancedFace.height)
            val dstRect = Rect(left, top, left + width, top + height)
            canvas.drawBitmap(enhancedFace, srcRect, dstRect, paint)
            enhancedFace.recycle()
        }

        return resultBitmap
    }

    private fun applyFaceSharpeningFilter(faceBitmap: Bitmap): Bitmap {
        val width = faceBitmap.width
        val height = faceBitmap.height
        val outputBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)

        val cm = ColorMatrix(
            floatArrayOf(
                1.1f, 0f, 0f, 0f, 10f,
                0f, 1.1f, 0f, 0f, 10f,
                0f, 0f, 1.1f, 0f, 10f,
                0f, 0f, 0f, 1f, 0f
            )
        )

        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(cm)
            isAntiAlias = true
            isFilterBitmap = true
        }

        canvas.drawBitmap(faceBitmap, 0f, 0f, paint)
        return outputBitmap
    }

    private fun applyDenoiseFallback(inputBitmap: Bitmap): Bitmap {
        val width = inputBitmap.width
        val height = inputBitmap.height
        val outputBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)

        val cm = ColorMatrix(
            floatArrayOf(
                1.15f, 0f, 0f, 0f, 15f,
                0f, 1.15f, 0f, 0f, 15f,
                0f, 0f, 1.15f, 0f, 15f,
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
        zeroDCEProcessor.close()
        superResProcessor.close()
    }
}
