package com.ndizigiye.loop.ui.create

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.MirrorMode
import androidx.camera.video.ExperimentalPersistentRecording
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ndizigiye.loop.api.ApiLimits
import com.ndizigiye.loop.api.SoundDto
import com.ndizigiye.loop.data.DiscoverRepository
import com.ndizigiye.loop.data.TEMP_FILE_PREFIX
import com.ndizigiye.loop.data.upload.UploadManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class CapturePhase {
    /** Nothing recorded yet. */
    IDLE,
    RECORDING,
    PAUSED,

    /** Stop requested; waiting for the file to be finalised. */
    FINALIZING,

    /** Finalised and handed to the post screen; it can be posted again or discarded. */
    FINISHED,
}

data class CameraUiState(
    val soundId: Long?,
    val sound: SoundDto? = null,
    /** The requested lens; the preview falls back to the other one when it is missing. */
    val lensFacing: Int = CameraSelector.LENS_FACING_FRONT,
    val torchOn: Boolean = false,
    val timerEnabled: Boolean = false,
    /** Seconds left before recording starts or resumes, while the timer counts down. */
    val countdown: Int? = null,
    val phase: CapturePhase = CapturePhase.IDLE,
    val recordedMs: Long = 0,
    /** Recorded time at each pause, i.e. where one segment ends and the next begins. */
    val segmentEndsMs: List<Long> = emptyList(),
    /** A discarded take is still closing; a new one can start once it has. */
    val recorderBusy: Boolean = false,
) {
    val hasTake: Boolean get() = phase != CapturePhase.IDLE
}

sealed interface CameraEvent {
    data class OpenPost(val videoUri: String, val soundId: Long?) : CameraEvent
    data class Message(val text: String) : CameraEvent
}

/**
 * Recording for [CameraScreen]. A take is one file: the record button starts it,
 * then pauses and resumes it ([Recording.pause] / [Recording.resume]), so each
 * pause marks a segment boundary. The screen binds [videoCapture] to the camera
 * together with its preview. Takes are persistent recordings, so a paused take
 * survives the camera closing while the screen is stopped and carries on once it
 * is bound again.
 */
class CameraViewModel(
    private val appContext: Context,
    discoverRepository: DiscoverRepository,
    private val uploadManager: UploadManager,
    private val soundId: Long?,
) : ViewModel() {

    private val _state = MutableStateFlow(CameraUiState(soundId = soundId))
    val state: StateFlow<CameraUiState> = _state.asStateFlow()

    private val _events = Channel<CameraEvent>(Channel.BUFFERED)
    val events: Flow<CameraEvent> = _events.receiveAsFlow()

    private val recorder: Recorder = Recorder.Builder()
        .setQualitySelector(QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
        .setTargetVideoEncodingBitRate(TARGET_VIDEO_BITRATE)
        .build()

    val videoCapture: VideoCapture<Recorder> = VideoCapture.Builder(recorder)
        .setMirrorMode(MirrorMode.MIRROR_MODE_ON_FRONT_ONLY)
        .build()

    private val mainExecutor = ContextCompat.getMainExecutor(appContext)
    private var take: Take? = null
    private var closingTakes = 0
    private var countdownJob: Job? = null

    /** The [CapturePhase.FINISHED] take; deleted on discard or when this screen goes away for good. */
    private var finished: File? = null

    init {
        if (soundId != null) {
            viewModelScope.launch {
                discoverRepository.sound(soundId).onSuccess { sound -> _state.update { it.copy(sound = sound) } }
            }
        }
    }

    /** Switches to the lens opposite [boundLens]; ignored once something is recorded. */
    fun flipLens(boundLens: Int) {
        val current = _state.value
        if (current.hasTake || current.countdown != null) return
        _state.update { it.copy(lensFacing = oppositeLens(boundLens), torchOn = false) }
    }

    fun toggleTimer() {
        _state.update { it.copy(timerEnabled = !it.timerEnabled) }
    }

    fun toggleTorch() {
        _state.update { it.copy(torchOn = !it.torchOn) }
    }

    /** The record button: starts, pauses or resumes the take; stops a running countdown. */
    fun onRecordClick() {
        val current = _state.value
        if (current.countdown != null) {
            cancelCountdown()
            return
        }
        when (current.phase) {
            CapturePhase.IDLE -> if (!current.recorderBusy) afterCountdown(::startTake)
            CapturePhase.RECORDING -> pause()
            CapturePhase.PAUSED -> afterCountdown(::resume)
            CapturePhase.FINALIZING, CapturePhase.FINISHED -> Unit
        }
    }

    /** Pauses an active take and stops a countdown, e.g. while asking whether to discard. */
    fun holdTake() {
        cancelCountdown()
        if (_state.value.phase == CapturePhase.RECORDING) pause()
    }

    /** The screen stopped and the camera closes with it: hold the take and turn the torch off. */
    fun onScreenStopped() {
        holdTake()
        if (_state.value.torchOn) _state.update { it.copy(torchOn = false) }
    }

    /** Stops the take; the post screen opens once the file is finalised. Reopens it for a finished take. */
    fun finish() {
        finished?.let { file ->
            _events.trySend(CameraEvent.OpenPost(Uri.fromFile(file).toString(), soundId))
            return
        }
        val current = take ?: return
        val phase = _state.value.phase
        if (phase != CapturePhase.RECORDING && phase != CapturePhase.PAUSED) return
        cancelCountdown()
        current.recording?.stop()
        _state.update { it.copy(phase = CapturePhase.FINALIZING) }
    }

    /** Throws the current take away; its file is deleted once the recorder has closed it. */
    fun discard() {
        cancelCountdown()
        finished?.let { file ->
            finished = null
            file.delete()
            resetTake()
            return
        }
        val current = take ?: return
        take = null
        closingTakes++
        current.recording?.close()
        resetTake()
        _state.update { it.copy(recorderBusy = true) }
    }

    override fun onCleared() {
        take?.let { abandoned ->
            abandoned.recording?.close()
            abandoned.file.delete()
        }
        take = null
        // A take still being uploaded (the task was swiped away) is left to the stale temp file cleanup.
        finished?.takeUnless { uploadManager.isUploading(Uri.fromFile(it)) }?.delete()
        finished = null
    }

    private fun afterCountdown(action: () -> Unit) {
        if (!_state.value.timerEnabled) {
            action()
            return
        }
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (seconds in COUNTDOWN_SECONDS downTo 1) {
                _state.update { it.copy(countdown = seconds) }
                delay(COUNTDOWN_STEP_MS)
            }
            _state.update { it.copy(countdown = null) }
            action()
        }
    }

    private fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        if (_state.value.countdown != null) _state.update { it.copy(countdown = null) }
    }

    // RECORD_AUDIO is checked right before audio is enabled.
    @SuppressLint("MissingPermission")
    @androidx.annotation.OptIn(ExperimentalPersistentRecording::class)
    private fun startTake() {
        if (take != null) return
        val file = File(appContext.cacheDir, "${TEMP_FILE_PREFIX}take-${System.currentTimeMillis()}.mp4")
        val options = FileOutputOptions.Builder(file)
            .setDurationLimitMillis(ApiLimits.MAX_RECORDING_MS)
            .build()
        val pending = recorder.prepareRecording(appContext, options)
            .asPersistentRecording()
            .let { if (hasAudioPermission()) it.withAudioEnabled() else it }
        val newTake = Take(file)
        newTake.recording = try {
            pending.start(mainExecutor) { event -> onRecordEvent(newTake, event) }
        } catch (e: IllegalStateException) {
            file.delete()
            _events.trySend(CameraEvent.Message("The camera is busy. Try again in a moment."))
            return
        }
        take = newTake
        _state.update { it.copy(phase = CapturePhase.RECORDING, recordedMs = 0, segmentEndsMs = emptyList()) }
    }

    private fun pause() {
        val current = take ?: return
        current.recording?.pause()
        _state.update { it.copy(phase = CapturePhase.PAUSED) }
    }

    private fun resume() {
        val current = take ?: return
        if (_state.value.phase != CapturePhase.PAUSED) return
        current.recording?.resume()
        _state.update { it.copy(phase = CapturePhase.RECORDING) }
    }

    private fun onRecordEvent(source: Take, event: VideoRecordEvent) {
        if (event is VideoRecordEvent.Finalize) {
            onFinalize(source, event)
            return
        }
        if (source !== take) return
        val recordedMs = (event.recordingStats.recordedDurationNanos / NANOS_PER_MILLI)
            .coerceAtMost(ApiLimits.MAX_RECORDING_MS)
        if (event is VideoRecordEvent.Pause) {
            _state.update { current ->
                val ends = current.segmentEndsMs
                val isNewBoundary = recordedMs > 0 && ends.lastOrNull() != recordedMs
                current.copy(recordedMs = recordedMs, segmentEndsMs = if (isNewBoundary) ends + recordedMs else ends)
            }
        } else {
            _state.update { it.copy(recordedMs = recordedMs) }
            if (recordedMs >= ApiLimits.MAX_RECORDING_MS) finish()
        }
    }

    private fun onFinalize(source: Take, event: VideoRecordEvent.Finalize) {
        if (source !== take) {
            // A discarded take has finished closing.
            source.file.delete()
            closingTakes = (closingTakes - 1).coerceAtLeast(0)
            _state.update { it.copy(recorderBusy = closingTakes > 0) }
            return
        }
        take = null
        val usable = !event.hasError() || event.error == VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED
        if (usable && source.file.length() > 0) {
            // Kept, so going back from the post screen returns to this take rather than an empty recorder.
            finished = source.file
            _state.update { it.copy(phase = CapturePhase.FINISHED) }
            _events.trySend(CameraEvent.OpenPost(Uri.fromFile(source.file).toString(), soundId))
        } else {
            resetTake()
            source.file.delete()
            _events.trySend(CameraEvent.Message(failureMessage(event.error)))
        }
    }

    private fun resetTake() {
        cancelCountdown()
        _state.update { it.copy(phase = CapturePhase.IDLE, recordedMs = 0, segmentEndsMs = emptyList()) }
    }

    private fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /** One recording in progress and the file it writes to. */
    private class Take(val file: File) {
        var recording: Recording? = null
    }

    private companion object {
        const val COUNTDOWN_SECONDS = 3
        const val COUNTDOWN_STEP_MS = 1_000L
        const val NANOS_PER_MILLI = 1_000_000L

        /** 4 Mbit/s, so a full 60 s take is about 30 MB (docs/feature-analysis.md, question 9). */
        const val TARGET_VIDEO_BITRATE = 4 * 1024 * 1024
    }
}

private fun failureMessage(error: Int): String = when (error) {
    VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA -> "That clip was too short. Try recording a little longer."
    VideoRecordEvent.Finalize.ERROR_INSUFFICIENT_STORAGE -> "Not enough storage space to save the video."
    VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE -> "Recording stopped because the camera was closed."
    else -> "Recording failed. Please try again."
}
