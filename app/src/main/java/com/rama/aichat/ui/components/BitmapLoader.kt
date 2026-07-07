package com.rama.aichat.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

@Composable
fun rememberBitmapFromPath(path: String?, maxDimension: Int = 1024): State<Bitmap?> =
    produceState<Bitmap?>(initialValue = null, key1 = path, key2 = maxDimension) {
        value = path?.let { imagePath ->
            withContext(Dispatchers.IO) {
                decodeSampledBitmapFromFile(imagePath, maxDimension)
            }
        }
    }

private fun decodeSampledBitmapFromFile(path: String, maxDimension: Int): Bitmap? {
    val file = File(path)
    if (!file.exists()) return null

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)

    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = false
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return BitmapFactory.decodeFile(path, options)
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
