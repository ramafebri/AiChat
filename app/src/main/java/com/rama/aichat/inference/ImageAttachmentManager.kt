package com.rama.aichat.inference

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class ImageAttachmentManager @Inject constructor(
    @ApplicationContext private val appContext: Context
) {
    fun createCameraCaptureUri(): Uri {
        val cameraDir = File(appContext.cacheDir, "camera_images").apply { mkdirs() }
        val tempFile = File(cameraDir, "capture_${System.currentTimeMillis()}.jpg")
        if (tempFile.exists()) {
            tempFile.delete()
        }
        return FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            tempFile
        )
    }

    suspend fun persistFromUri(uri: Uri): String = withContext(Dispatchers.IO) {
        val imageDir = File(appContext.filesDir, "chat_images").apply { mkdirs() }
        val targetFile = File(imageDir, "${UUID.randomUUID()}.jpg")

        appContext.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Unable to read selected image.")

        targetFile.absolutePath
    }

    suspend fun loadBitmapForInference(path: String, maxDimension: Int = 1024): Bitmap =
        withContext(Dispatchers.IO) {
            val file = File(path)
            if (!file.exists()) {
                throw IllegalStateException("Image file not found.")
            }

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val sampleSize = calculateInSampleSize(
                width = bounds.outWidth,
                height = bounds.outHeight,
                maxDimension = maxDimension
            )

            val decodeOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = false
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
                ?: throw IllegalStateException("Failed to decode image for inference.")
        }

    private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        if (width <= 0 || height <= 0 || maxDimension <= 0) return 1
        val largestDimension = max(width, height)
        var sampleSize = 1
        while (largestDimension / sampleSize > maxDimension) {
            sampleSize *= 2
        }
        return sampleSize
    }
}
