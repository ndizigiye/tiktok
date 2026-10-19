package com.ndizigiye.loop.ui.create

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.ndizigiye.loop.ui.theme.LoopColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.roundToInt

/** Enough pixels for a 160 dp tall tile on a dense screen. */
private const val COVER_MAX_HEIGHT_PX = 480

sealed interface CoverState {
    data object Loading : CoverState
    data class Ready(val image: ImageBitmap) : CoverState
    data object Unavailable : CoverState
}

/** The first frame of the video at [uri], downscaled for a cover tile; null when it can't be read. */
internal suspend fun loadCoverFrame(context: Context, uri: Uri): ImageBitmap? =
    withContext(Dispatchers.IO) { readFirstFrame(context, uri)?.asImageBitmap() }

private fun readFirstFrame(context: Context, uri: Uri): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.scaledToHeight(COVER_MAX_HEIGHT_PX)
    } catch (e: RuntimeException) {
        // Unreadable, unsupported or no longer accessible media (IllegalArgumentException,
        // SecurityException or a plain RuntimeException from the native retriever).
        null
    } finally {
        try {
            retriever.release()
        } catch (e: IOException) {
            // Nothing left to clean up.
        }
    }
}

private fun Bitmap.scaledToHeight(maxHeight: Int): Bitmap {
    if (height <= maxHeight) return this
    val scaledWidth = (width * maxHeight.toFloat() / height).roundToInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(this, scaledWidth, maxHeight, true)
    if (scaled !== this) recycle()
    return scaled
}

/** Rounded cover tile: the frame, a spinner while it loads, or a neutral placeholder. */
@Composable
internal fun VideoCover(cover: CoverState, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(LoopColors.Placeholder),
        contentAlignment = Alignment.Center,
    ) {
        when (cover) {
            CoverState.Loading -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
            )
            is CoverState.Ready -> Image(
                bitmap = cover.image,
                contentDescription = "Video cover",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            CoverState.Unavailable -> Icon(
                imageVector = Icons.Outlined.Videocam,
                contentDescription = null,
                tint = LoopColors.TextMuted,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}
