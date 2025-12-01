package com.meq.colourchecker.camera

import android.view.ViewGroup
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import com.meq.colourchecker.processing.AnalysisFrame
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    permissionsRequired: Boolean,
    torchEnabled: Boolean,
    onRequestPermissions: () -> Unit,
    onFrame: (AnalysisFrame) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val cameraRef = remember { mutableStateOf<Camera?>(null) }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            cameraProviderFuture.addListener(
                {
                    cameraProviderFuture.get().unbindAll()
                },
                ContextCompat.getMainExecutor(context)
            )
            analyzerExecutor.shutdown()
        }
    }

    LaunchedEffect(permissionsRequired) {
        if (!permissionsRequired) {
            bindCamera(
                lifecycleOwner = lifecycleOwner,
                previewView = previewView,
                cameraProviderFuture = cameraProviderFuture,
                analyzerExecutor = analyzerExecutor,
                cameraRef = cameraRef,
                onFrame = onFrame
            )
        } else {
            cameraProviderFuture.addListener(
                {
                    cameraProviderFuture.get().unbindAll()
                },
                ContextCompat.getMainExecutor(context)
            )
            cameraRef.value = null
        }
    }

    LaunchedEffect(torchEnabled, cameraRef.value) {
        cameraRef.value?.cameraControl?.enableTorch(torchEnabled)
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (permissionsRequired) {
            Button(onClick = onRequestPermissions) {
                Text("Grant camera permission")
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    previewView.apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                }
            )
        }
    }
}

private suspend fun bindCamera(
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    cameraProviderFuture: ListenableFuture<ProcessCameraProvider>,
    analyzerExecutor: ExecutorService,
    cameraRef: MutableState<Camera?>,
    onFrame: (AnalysisFrame) -> Unit
) {
    val cameraProvider = cameraProviderFuture.awaitMain(previewView)
    cameraProvider.unbindAll()

    val preview = Preview.Builder()
        .build()
        .also { it.setSurfaceProvider(previewView.surfaceProvider) }

    val analysis = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()

    analysis.setAnalyzer(analyzerExecutor) { image ->
        try {
            val rgba = image.toRgbaByteArray()
            onFrame(
                AnalysisFrame(
                    timestamp = System.currentTimeMillis(),
                    width = image.width,
                    height = image.height,
                    rotationDegrees = image.imageInfo.rotationDegrees,
                    rgbaPixels = rgba
                )
            )
        } finally {
            image.close()
        }
    }

    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    val camera = cameraProvider.bindToLifecycle(
        lifecycleOwner,
        cameraSelector,
        preview,
        analysis
    )
    cameraRef.value = camera
}

private suspend fun ListenableFuture<ProcessCameraProvider>.awaitMain(previewView: PreviewView): ProcessCameraProvider =
    suspendCancellableCoroutine { cont ->
        addListener(
            {
                cont.resume(get())
            },
            ContextCompat.getMainExecutor(previewView.context)
        )
    }
