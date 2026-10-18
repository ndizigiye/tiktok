package com.ndizigiye.loop.data.upload

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.ndizigiye.loop.LoopApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground service of type dataSync for as long as an [UploadManager] upload runs, so
 * the upload carries on when the user leaves the app. Its notification shows the
 * progress (with a cancel action while the file is sent) and is then replaced by the
 * result. Runs without a visible notification when the user has turned notifications off.
 */
class UploadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val uploadManager: UploadManager
        get() = (application as LoopApplication).container.uploadManager

    private var following: Job? = null

    /** Stopping with the latest start id keeps the service alive for an upload started meanwhile. */
    private var latestStartId = 0

    override fun onCreate() {
        super.onCreate()
        UploadNotifications.createChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        if (intent?.action == ACTION_CANCEL) {
            (uploadManager.state.value as? UploadState.Uploading)?.let { uploadManager.cancel(it.id) }
        } else {
            // Required soon after startForegroundService(), even when the upload has already ended.
            startInForeground()
        }
        if (following == null) following = scope.launch { followUploads() }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Android 15 limits dataSync services to six hours a day; the upload ends with the service. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        (uploadManager.state.value as? UploadState.Uploading)?.let { uploadManager.cancel(it.id) }
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // ServiceCompat leaves out the service type before Android 10, which doesn't know it.
    @SuppressLint("InlinedApi")
    private fun startInForeground() {
        val progress = (uploadManager.state.value as? UploadState.Uploading)?.progress ?: 0f
        ServiceCompat.startForeground(
            this,
            UploadNotifications.NOTIFICATION_ID,
            UploadNotifications.progress(this, progress),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private suspend fun followUploads() {
        uploadManager.state.collect { state ->
            if (state is UploadState.Uploading) {
                UploadNotifications.show(this, UploadNotifications.progress(this, state.progress))
                // Android drops notification updates posted faster than a few per second.
                delay(PROGRESS_UPDATE_INTERVAL_MS)
            } else {
                val result = state?.let { UploadNotifications.result(this, it) }
                if (result == null) {
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                } else {
                    // Detached, the notification outlives the service and becomes the result.
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
                    UploadNotifications.show(this, result)
                }
                stopSelf(latestStartId)
            }
        }
    }

    companion object {
        internal const val ACTION_CANCEL = "com.ndizigiye.loop.action.CANCEL_UPLOAD"
        private const val PROGRESS_UPDATE_INTERVAL_MS = 500L

        /**
         * Starts the service for the upload that [UploadManager] just began. When the app is
         * already in the background Android refuses; the upload then runs without it.
         */
        internal fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, UploadService::class.java))
            } catch (e: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException (Android 12+) or a background start ban.
            }
        }
    }
}
