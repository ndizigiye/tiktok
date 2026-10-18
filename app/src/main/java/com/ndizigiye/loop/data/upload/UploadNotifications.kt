package com.ndizigiye.loop.data.upload

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

/**
 * The upload notification: progress while [UploadService] runs in the foreground, then
 * the result. One notification id throughout, so each upload replaces the last one's
 * result. Tapping it opens the app.
 */
internal object UploadNotifications {

    const val NOTIFICATION_ID = 1

    private const val CHANNEL_ID = "uploads"
    private const val OPEN_APP_REQUEST = 0
    private const val CANCEL_REQUEST = 1

    /** Low importance: progress and results show silently, without a heads-up. */
    fun createChannel(context: Context) {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName("Uploads")
            .setDescription("Progress and result of the videos you post")
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    fun progress(context: Context, progress: Float): Notification {
        val processing = progress >= 1f
        val percent = (progress * 100).roundToInt().coerceIn(0, 100)
        return builder(context)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Posting your video")
            .setContentText(if (processing) "Processing..." else "Uploading... $percent%")
            .setProgress(100, percent, processing)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, "Cancel", cancelIntent(context))
            .build()
    }

    /** The outcome of a finished upload; null for one that needs no notification (cancelled). */
    fun result(context: Context, state: UploadState): Notification? = when (state) {
        is UploadState.Posted -> builder(context)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle("Video posted")
            .setContentText("Your video is now on your profile.")
            .setAutoCancel(true)
            .build()
        is UploadState.Failed -> builder(context)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Couldn't post your video")
            .setContentText(state.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(state.message))
            .setAutoCancel(true)
            .build()
        is UploadState.Uploading, is UploadState.Cancelled -> null
    }

    /** Shows [notification] unless the user has turned notifications off (Android 13+). */
    fun show(context: Context, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun builder(context: Context): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentIntent(openAppIntent(context))
            .setPriority(NotificationCompat.PRIORITY_LOW)

    /** Brings the app's task to the front as the launcher would, without starting a second activity. */
    private fun openAppIntent(context: Context): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(
            context,
            OPEN_APP_REQUEST,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun cancelIntent(context: Context): PendingIntent =
        PendingIntent.getService(
            context,
            CANCEL_REQUEST,
            Intent(context, UploadService::class.java).setAction(UploadService.ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
