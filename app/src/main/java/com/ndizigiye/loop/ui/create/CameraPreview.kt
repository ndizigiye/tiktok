package com.ndizigiye.loop.ui.create

import androidx.camera.core.Camera
import androidx.camera.core.CameraInfoUnavailableException
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner

private const val CAMERA_ERROR_MESSAGE = "Couldn't start the camera."

/** The camera the preview is bound to. */
internal class BoundCamera(
    val camera: Camera,
    val lensFacing: Int,
    /** The device also has a camera facing the other way. */
    val canSwitchLens: Boolean,
) {
    val hasFlash: Boolean get() = camera.cameraInfo.hasFlashUnit()
}

internal fun oppositeLens(lensFacing: Int): Int =
    if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
        CameraSelector.LENS_FACING_BACK
    } else {
        CameraSelector.LENS_FACING_FRONT
    }

/**
 * Camera preview filling [modifier]. Binds a 16:9 preview and [videoCapture] to the
 * current lifecycle owner with [lensFacing] (or the other lens when that one is
 * missing), rebinds when the lens changes and unbinds when it leaves. The screen
 * stays on while [keepScreenOn].
 */
@Composable
internal fun CameraPreview(
    videoCapture: VideoCapture<Recorder>,
    lensFacing: Int,
    keepScreenOn: Boolean,
    onBound: (BoundCamera?) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnBound by rememberUpdatedState(onBound)
    val currentOnError by rememberUpdatedState(onError)
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val preview = remember {
        Preview.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                    .build(),
            )
            .build()
    }

    AndroidView(factory = { previewView }, modifier = modifier, update = { it.keepScreenOn = keepScreenOn })

    DisposableEffect(context, lifecycleOwner, lensFacing, videoCapture) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false
        providerFuture.addListener(
            {
                if (disposed) return@addListener
                val cameraProvider = try {
                    providerFuture.get()
                } catch (e: Exception) {
                    currentOnError(CAMERA_ERROR_MESSAGE)
                    return@addListener
                }
                provider = cameraProvider
                preview.setSurfaceProvider(previewView.surfaceProvider)
                val bound = cameraProvider.bindPreviewAndCapture(lifecycleOwner, lensFacing, preview, videoCapture)
                if (bound != null) currentOnBound(bound) else currentOnError(CAMERA_ERROR_MESSAGE)
            },
            ContextCompat.getMainExecutor(context),
        )
        onDispose {
            disposed = true
            provider?.unbindAll()
            currentOnBound(null)
        }
    }
}

private fun ProcessCameraProvider.bindPreviewAndCapture(
    lifecycleOwner: LifecycleOwner,
    preferredLens: Int,
    vararg useCases: UseCase,
): BoundCamera? {
    val lenses = listOf(preferredLens, oppositeLens(preferredLens)).filter { hasLens(it) }
    val lens = lenses.firstOrNull() ?: return null
    return try {
        unbindAll()
        val camera = bindToLifecycle(lifecycleOwner, selectorFor(lens), *useCases)
        BoundCamera(camera, lens, canSwitchLens = lenses.size > 1)
    } catch (e: IllegalStateException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
}

private fun ProcessCameraProvider.hasLens(lensFacing: Int): Boolean =
    try {
        hasCamera(selectorFor(lensFacing))
    } catch (e: CameraInfoUnavailableException) {
        false
    }

private fun selectorFor(lensFacing: Int): CameraSelector =
    CameraSelector.Builder().requireLensFacing(lensFacing).build()
