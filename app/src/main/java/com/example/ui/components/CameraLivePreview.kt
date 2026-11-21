package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ObsidianSurfaceElevated

private const val TAG = "CameraLivePreview"

@Composable
fun CameraLivePreviewCard(
    onCaptureFrame: (Bitmap) -> Unit,
    onCloseCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    DisposableEffect(lensFacing, lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        var provider: ProcessCameraProvider? = null

        cameraProviderFuture.addListener({
            runCatching {
                provider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val selector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()
                provider?.unbindAll()
                provider?.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
            }.onFailure { err ->
                Log.e(TAG, "Camera bind failed", err)
            }
        }, executor)

        onDispose {
            provider?.unbindAll()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(ObsidianSurfaceElevated)
            .border(1.dp, ElectricCyan.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                },
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                    .testTag("camera_flip_button")
            ) {
                Icon(
                    imageVector = Icons.Default.FlipCameraAndroid,
                    contentDescription = "カメラ切り替え",
                    tint = Color.White
                )
            }

            IconButton(
                onClick = onCloseCamera,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                    .testTag("camera_close_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "カメラを閉じる",
                    tint = Color.White
                )
            }
        }

        FilledTonalButton(
            onClick = {
                val mainExecutor = ContextCompat.getMainExecutor(context)
                imageCapture.takePicture(
                    mainExecutor,
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val bmp = imageProxyToScaledBitmap(image)
                            image.close()
                            if (bmp != null) {
                                onCaptureFrame(bmp)
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Log.e(TAG, "Frame capture failed", exception)
                        }
                    }
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp)
                .testTag("camera_capture_button")
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                modifier = Modifier.padding(end = 6.dp)
            )
            Text(
                text = "この映像をGeminiに送る",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

private fun imageProxyToScaledBitmap(image: ImageProxy): Bitmap? {
    val raw = runCatching { image.toBitmap() }.getOrNull() ?: return null
    val rotation = image.imageInfo.rotationDegrees
    val rotated = if (rotation != 0) {
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true).also {
            if (it != raw) raw.recycle()
        }
    } else {
        raw
    }
    val maxDim = 960
    val longest = maxOf(rotated.width, rotated.height)
    if (longest <= maxDim) return rotated
    val scale = maxDim.toFloat() / longest.toFloat()
    val w = (rotated.width * scale).toInt().coerceAtLeast(1)
    val h = (rotated.height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(rotated, w, h, true).also {
        if (it != rotated) rotated.recycle()
    }
}
