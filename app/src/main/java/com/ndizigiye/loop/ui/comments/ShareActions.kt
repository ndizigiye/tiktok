package com.ndizigiye.loop.ui.comments

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

/** The public web page of a video: `<baseUrl>v/<id>` (see docs/api.md "Web"). */
internal fun shareLinkOf(baseUrl: String, videoId: Long): String = "${baseUrl}v/$videoId"

/** Opens the system chooser to send [caption] and [link] as text; false when nothing can handle it. */
internal fun Context.launchShareChooser(caption: String, link: String): Boolean {
    val text = if (caption.isBlank()) link else "${caption.trim()}\n$link"
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(send, "Share video")
    if (this !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        startActivity(chooser)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}

/**
 * True before Android 10 while WRITE_EXTERNAL_STORAGE is not granted: saving to the
 * shared Movies folder needs it there. From Android 10 on DownloadManager needs none.
 */
internal fun Context.needsStoragePermissionToSave(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
        PackageManager.PERMISSION_GRANTED

/**
 * Downloads [url] with the system DownloadManager to Movies/Loop_<id>.mp4. Before
 * Android 10 the shared Movies folder needs WRITE_EXTERNAL_STORAGE (see
 * [needsStoragePermissionToSave]); without it the download falls back to the app's
 * own Movies folder. False when no download could be started.
 */
internal fun Context.enqueueVideoDownload(url: String, videoId: Long): Boolean {
    val manager = getSystemService(DownloadManager::class.java) ?: return false
    val fileName = "Loop_$videoId.mp4"
    fun request(): DownloadManager.Request =
        DownloadManager.Request(url.toUri())
            .setTitle(fileName)
            .setDescription("Loop video")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
    return try {
        manager.enqueue(request().setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES, fileName))
        true
    } catch (e: IllegalArgumentException) {
        // Not an http(s) URL.
        false
    } catch (e: SecurityException) {
        enqueueInAppFolder(manager, request(), fileName)
    } catch (e: IllegalStateException) {
        enqueueInAppFolder(manager, request(), fileName)
    }
}

private fun Context.enqueueInAppFolder(
    manager: DownloadManager,
    request: DownloadManager.Request,
    fileName: String,
): Boolean =
    try {
        manager.enqueue(request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_MOVIES, fileName))
        true
    } catch (e: SecurityException) {
        false
    } catch (e: IllegalStateException) {
        false
    }
