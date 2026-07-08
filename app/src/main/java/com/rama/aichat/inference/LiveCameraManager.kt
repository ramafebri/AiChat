package com.rama.aichat.inference

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiveCameraManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val imageAttachmentManager: ImageAttachmentManager
) {
    private val bindMutex = Mutex()
    private val frameLock = Any()

    @Volatile
    private var latestFrameBytes: ByteArray? = null

    @Volatile
    private var latestFrameWidth: Int = 0

    @Volatile
    private var latestFrameHeight: Int = 0

    @Volatile
    private var latestFrameRotation: Int = 0

    private var cameraProvider: ProcessCameraProvider? = null
    private var boundLifecycleOwner: LifecycleOwner? = null
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    suspend fun bind(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        bindMutex.withLock {
            if (boundLifecycleOwner === lifecycleOwner && cameraProvider != null) {
                return
            }
            unbindInternal()

            val provider = obtainCameraProvider()
            withContext(Dispatchers.Main) {
                cameraProvider = provider
                boundLifecycleOwner = lifecycleOwner

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    storeLatestFrame(imageProxy)
                }

                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
            }
        }
    }

    suspend fun unbind() {
        bindMutex.withLock {
            withContext(Dispatchers.Main) {
                unbindInternal()
            }
        }
    }

    suspend fun captureFrameBitmap(maxDimension: Int = 512): Bitmap? = withContext(Dispatchers.Default) {
        val snapshot = synchronized(frameLock) {
            val bytes = latestFrameBytes ?: return@withContext null
            FrameSnapshot(
                bytes = bytes.copyOf(),
                width = latestFrameWidth,
                height = latestFrameHeight,
                rotation = latestFrameRotation
            )
        }

        if (snapshot.width <= 0 || snapshot.height <= 0) return@withContext null

        val decoded = BitmapFactory.decodeByteArray(snapshot.bytes, 0, snapshot.bytes.size)
            ?: return@withContext null

        val rotated = if (snapshot.rotation != 0) {
            val matrix = Matrix().apply { postRotate(snapshot.rotation.toFloat()) }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
                if (it != decoded) {
                    decoded.recycle()
                }
            }
        } else {
            decoded
        }

        imageAttachmentManager.downscaleBitmap(rotated, maxDimension)
    }

    private suspend fun obtainCameraProvider(): ProcessCameraProvider {
        return withContext(Dispatchers.IO) {
            ProcessCameraProvider.getInstance(appContext).get()
        }
    }

    private fun unbindInternal() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        boundLifecycleOwner = null
        synchronized(frameLock) {
            latestFrameBytes = null
            latestFrameWidth = 0
            latestFrameHeight = 0
            latestFrameRotation = 0
        }
    }

    private fun storeLatestFrame(imageProxy: ImageProxy) {
        try {
            if (imageProxy.format != ImageFormat.YUV_420_888) {
                return
            }

            val nv21 = yuv420ToNv21(imageProxy)
            val yuvImage = YuvImage(
                nv21,
                ImageFormat.NV21,
                imageProxy.width,
                imageProxy.height,
                null
            )
            val jpegStream = ByteArrayOutputStream()
            yuvImage.compressToJpeg(
                Rect(0, 0, imageProxy.width, imageProxy.height),
                85,
                jpegStream
            )

            synchronized(frameLock) {
                latestFrameBytes = jpegStream.toByteArray()
                latestFrameWidth = imageProxy.width
                latestFrameHeight = imageProxy.height
                latestFrameRotation = imageProxy.imageInfo.rotationDegrees
            }
        } finally {
            imageProxy.close()
        }
    }

    private fun yuv420ToNv21(imageProxy: ImageProxy): ByteArray {
        val width = imageProxy.width
        val height = imageProxy.height
        val ySize = width * height
        val uvSize = width * height / 2
        val nv21 = ByteArray(ySize + uvSize)

        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer

        val yRowStride = imageProxy.planes[0].rowStride
        val yPixelStride = imageProxy.planes[0].pixelStride
        var outputOffset = 0
        for (row in 0 until height) {
            var inputOffset = row * yRowStride
            for (col in 0 until width) {
                nv21[outputOffset++] = yBuffer.get(inputOffset)
                inputOffset += yPixelStride
            }
        }

        val chromaHeight = height / 2
        val chromaWidth = width / 2
        val vRowStride = imageProxy.planes[2].rowStride
        val vPixelStride = imageProxy.planes[2].pixelStride
        val uRowStride = imageProxy.planes[1].rowStride
        val uPixelStride = imageProxy.planes[1].pixelStride

        var uvOffset = ySize
        for (row in 0 until chromaHeight) {
            var vInputOffset = row * vRowStride
            var uInputOffset = row * uRowStride
            for (col in 0 until chromaWidth) {
                nv21[uvOffset++] = vBuffer.get(vInputOffset)
                nv21[uvOffset++] = uBuffer.get(uInputOffset)
                vInputOffset += vPixelStride
                uInputOffset += uPixelStride
            }
        }

        return nv21
    }

    private data class FrameSnapshot(
        val bytes: ByteArray,
        val width: Int,
        val height: Int,
        val rotation: Int
    )
}
