package com.example.ultraenhance.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.ultraenhance.ui.viewmodel.EnhancementMode
import java.io.OutputStream

class ImageRepository {

    fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.isMutableRequired = true
                }
            } else {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun executeUnifiedPipeline(
        context: Context,
        inputBitmap: Bitmap,
        onStageProgress: (stageMessage: String, progressPercent: Int) -> Unit
    ): Pair<Bitmap, Boolean> {
        val pipeline = UnifiedAIPipeline(context)
        val result = pipeline.execute(inputBitmap, onStageProgress)
        pipeline.close()
        return result
    }

    fun processImagePipeline(
        context: Context,
        inputBitmap: Bitmap,
        mode: EnhancementMode,
        onProgress: (currentTile: Int, totalTiles: Int) -> Unit
    ): Pair<Bitmap, Boolean> {
        var currentBitmap = inputBitmap
        var isFallbackUsed = false

        try {
            when (mode) {
                EnhancementMode.FULL -> {
                    val zeroDCEProcessor = ZeroDCEProcessor(context)
                    val isZeroDCEAvailable = zeroDCEProcessor.isModelAvailable()
                    val stage1Output = zeroDCEProcessor.process(currentBitmap)
                    zeroDCEProcessor.close()

                    val superResProcessor = SuperResProcessor(context)
                    val isSuperResAvailable = superResProcessor.isModelAvailable()
                    val stage2Output = superResProcessor.process(stage1Output, onProgress)
                    superResProcessor.close()

                    currentBitmap = stage2Output
                    isFallbackUsed = !isZeroDCEAvailable || !isSuperResAvailable
                }
                EnhancementMode.LOW_LIGHT -> {
                    val zeroDCEProcessor = ZeroDCEProcessor(context)
                    val isZeroDCEAvailable = zeroDCEProcessor.isModelAvailable()
                    val stage1Output = zeroDCEProcessor.process(currentBitmap)
                    zeroDCEProcessor.close()

                    currentBitmap = stage1Output
                    isFallbackUsed = !isZeroDCEAvailable
                }
                EnhancementMode.SUPER_RES -> {
                    val superResProcessor = SuperResProcessor(context)
                    val isSuperResAvailable = superResProcessor.isModelAvailable()
                    val stage2Output = superResProcessor.process(currentBitmap, onProgress)
                    superResProcessor.close()

                    currentBitmap = stage2Output
                    isFallbackUsed = !isSuperResAvailable
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            isFallbackUsed = true
        }

        return Pair(currentBitmap, isFallbackUsed)
    }

    fun saveImageToGallery(context: Context, bitmap: Bitmap): Uri? {
        val filename = "UltraEnhance_${System.currentTimeMillis()}.png"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/UltraEnhance")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        imageUri?.let { uri ->
            try {
                resolver.openOutputStream(uri)?.use { outputStream: OutputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
                return uri
            } catch (e: Exception) {
                e.printStackTrace()
                resolver.delete(uri, null, null)
            }
        }
        return null
    }
}
