package com.ndizigiye.loop.ui.create

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ndizigiye.loop.ui.common.LocalSnackbarHostState
import com.ndizigiye.loop.ui.common.rememberBackAction
import com.ndizigiye.loop.ui.navigation.LocalAppContainer
import com.ndizigiye.loop.ui.navigation.LocalNavigator
import com.ndizigiye.loop.ui.navigation.MainTab
import kotlinx.coroutines.launch

/** Why the discard dialog is showing. */
private enum class DiscardReason { CLOSE, RETAKE }

/**
 * Full-screen recorder. Tap to record, tap to pause, tap again to add another
 * segment to the same take, then continue to the post screen. Videos can also be
 * picked from the gallery. [soundId] is a sound chosen beforehand, if any.
 */
@Composable
fun CameraScreen(soundId: Long?) {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val snackbarHostState = LocalSnackbarHostState.current
    val context = LocalContext.current
    val appContext = context.applicationContext
    val viewModel: CameraViewModel = viewModel(key = "camera:$soundId") {
        CameraViewModel(appContext, container.discoverRepository, container.uploadManager, soundId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permissions = rememberCapturePermissionState()
    val back = rememberBackAction()
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { message -> scope.launch { snackbarHostState.showSnackbar(message) } }

    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            appContext.keepReadAccess(uri)
            navigator.openPost(uri.toString(), soundId)
        }
    }
    val openGallery: () -> Unit = {
        try {
            pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
        } catch (e: ActivityNotFoundException) {
            showMessage("No app is available to pick a video.")
        }
    }
    val openDiscover: () -> Unit = { navigator.selectTab(MainTab.DISCOVER) }
    val onAddSound = openDiscover.takeIf { state.soundId == null && !state.hasTake }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    is CameraEvent.OpenPost -> navigator.openPost(event.videoUri, event.soundId)
                    is CameraEvent.Message -> launch { snackbarHostState.showSnackbar(event.text) }
                }
            }
        }
    }
    DisposableEffect(viewModel, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.onScreenStopped()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var discardReason by rememberSaveable { mutableStateOf<DiscardReason?>(null) }
    val askToDiscard: (DiscardReason) -> Unit = { reason ->
        if (state.phase != CapturePhase.FINALIZING) {
            viewModel.holdTake()
            discardReason = reason
        }
    }
    val onClose: () -> Unit = { if (state.hasTake) askToDiscard(DiscardReason.CLOSE) else back() }
    BackHandler(enabled = state.hasTake) { askToDiscard(DiscardReason.CLOSE) }
    LaunchedEffect(state.hasTake) {
        if (!state.hasTake) discardReason = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (permissions.granted) {
            CameraCapture(
                state = state,
                viewModel = viewModel,
                onClose = onClose,
                onAddSound = onAddSound,
                onUpload = openGallery,
                onRetake = { askToDiscard(DiscardReason.RETAKE) },
                onError = showMessage,
            )
        } else {
            if (permissions.showRationale) {
                CapturePermissionRationale(
                    onAllow = permissions.request,
                    onOpenSettings = {
                        if (!context.openAppSettings()) showMessage("Allow camera and microphone access in Settings.")
                    },
                    onUpload = openGallery,
                )
            }
            CameraTopBar(
                onClose = onClose,
                closeEnabled = true,
                soundLabel = soundLabel(state),
                onSoundClick = onAddSound,
                modifier = Modifier.statusBarsPadding(),
            )
        }
    }

    val reason = discardReason
    if (reason != null) {
        DiscardDialog(
            reason = reason,
            onConfirm = {
                discardReason = null
                viewModel.discard()
                if (reason == DiscardReason.CLOSE) back()
            },
            onDismiss = { discardReason = null },
        )
    }
}

/** The live preview with all recording controls on top. */
@Composable
private fun CameraCapture(
    state: CameraUiState,
    viewModel: CameraViewModel,
    onClose: () -> Unit,
    onAddSound: (() -> Unit)?,
    onUpload: () -> Unit,
    onRetake: () -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var boundCamera by remember { mutableStateOf<BoundCamera?>(null) }
    val camera = boundCamera
    LaunchedEffect(camera, state.torchOn) {
        if (camera != null && camera.hasFlash) camera.camera.cameraControl.enableTorch(state.torchOn)
    }
    val controlsVisible = state.countdown == null &&
        (state.phase == CapturePhase.IDLE || state.phase == CapturePhase.PAUSED)
    // A take is recorded hands-free for up to a minute, longer than a typical screen timeout.
    val keepScreenOn = state.countdown != null ||
        state.phase == CapturePhase.RECORDING || state.phase == CapturePhase.PAUSED

    Box(modifier = modifier.fillMaxSize()) {
        CameraPreview(
            videoCapture = viewModel.videoCapture,
            lensFacing = state.lensFacing,
            keepScreenOn = keepScreenOn,
            onBound = { boundCamera = it },
            onError = onError,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
            ) {
                RecordingProgressBar(
                    recordedMs = state.recordedMs,
                    segmentEndsMs = state.segmentEndsMs,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
                CameraTopBar(
                    onClose = onClose,
                    closeEnabled = state.phase != CapturePhase.FINALIZING,
                    soundLabel = soundLabel(state),
                    onSoundClick = onAddSound,
                )
            }
            AnimatedVisibility(
                visible = controlsVisible,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 84.dp, end = 8.dp),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                CameraToolbar(
                    canFlip = camera != null && camera.canSwitchLens && !state.hasTake,
                    onFlip = { if (camera != null) viewModel.flipLens(camera.lensFacing) },
                    timerEnabled = state.timerEnabled,
                    onToggleTimer = viewModel::toggleTimer,
                    flashAvailable = camera != null && camera.hasFlash,
                    torchOn = state.torchOn,
                    onToggleTorch = viewModel::toggleTorch,
                )
            }
            CaptureBottomBar(
                state = state,
                recordEnabled = camera != null && !state.recorderBusy &&
                    state.phase != CapturePhase.FINALIZING && state.phase != CapturePhase.FINISHED,
                onRecord = viewModel::onRecordClick,
                onUpload = onUpload,
                onRetake = onRetake,
                onFinish = viewModel::finish,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
            )
        }
        state.countdown?.let { seconds -> CountdownOverlay(seconds = seconds) }
    }
}

@Composable
private fun DiscardDialog(reason: DiscardReason, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val (title, confirmLabel) = when (reason) {
        DiscardReason.CLOSE -> "Discard this video?" to "Discard"
        DiscardReason.RETAKE -> "Start over?" to "Retake"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep") } },
        title = { Text(title) },
        text = { Text("The clips you've recorded so far will be deleted.") },
    )
}

private fun soundLabel(state: CameraUiState): String =
    state.sound?.title ?: if (state.soundId == null) "Add sound" else "Selected sound"
