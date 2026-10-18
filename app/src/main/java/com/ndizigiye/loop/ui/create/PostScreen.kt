package com.ndizigiye.loop.ui.create

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ndizigiye.loop.api.ApiLimits
import com.ndizigiye.loop.ui.common.LocalSnackbarHostState
import com.ndizigiye.loop.ui.common.LoopTopBar
import com.ndizigiye.loop.ui.common.rememberBackAction
import com.ndizigiye.loop.ui.common.rememberMessageScope
import com.ndizigiye.loop.ui.navigation.LocalAppContainer
import com.ndizigiye.loop.ui.navigation.LocalNavigator
import com.ndizigiye.loop.ui.navigation.MainTab
import com.ndizigiye.loop.ui.theme.LoopColors
import com.ndizigiye.loop.ui.theme.LoopViolet
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Last step before publishing: caption with #hashtag / @mention helpers, a cover
 * frame, the sound, and an upload with progress that can be cancelled. The upload
 * runs in the app-wide UploadManager and carries on when the user leaves the app.
 * [videoUri] is a file:// recording or a content:// pick.
 */
@Composable
fun PostScreen(videoUri: String, soundId: Long?) {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val snackbarHostState = LocalSnackbarHostState.current
    val appContext = LocalContext.current.applicationContext
    val viewModel: PostViewModel = viewModel(key = "post:$videoUri:$soundId") {
        PostViewModel(appContext, container.uploadManager, container.discoverRepository, videoUri, soundId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var caption by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    val captionFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val back = rememberBackAction()
    val editable = !state.isUploading && !state.isPosted
    val messageScope = rememberMessageScope()

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner, messageScope) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    PostEvent.Posted -> {
                        navigator.selectTab(MainTab.PROFILE)
                        // This screen leaves right away; the message has to outlive it.
                        messageScope.launch { snackbarHostState.showSnackbar("Posted!") }
                    }
                    PostEvent.Cancelled -> launch { snackbarHostState.showSnackbar("Upload cancelled") }
                    is PostEvent.Failed -> launch { snackbarHostState.showSnackbar(event.message) }
                }
            }
        }
    }

    // The upload survives leaving the app, but back cancels it rather than leaving it to run unseen.
    BackHandler(enabled = state.isUploading, onBack = viewModel::cancelUpload)
    val onBack: () -> Unit = { if (state.isUploading) viewModel.cancelUpload() else back() }
    // Asks for notification access first, for the upload's progress notification.
    val post = rememberAfterNotificationPermission { viewModel.post(caption.text) }

    val addToken: (String) -> Unit = { symbol ->
        caption = caption.withToken(symbol, ApiLimits.CAPTION_MAX)
        captionFocus.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        LoopTopBar(title = "Post", onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CaptionField(
                    value = caption,
                    onValueChange = { caption = it.limitedTo(ApiLimits.CAPTION_MAX) },
                    enabled = editable,
                    focusRequester = captionFocus,
                    modifier = Modifier.weight(1f),
                )
                VideoCover(cover = state.cover, modifier = Modifier.size(width = 90.dp, height = 160.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { addToken("#") }, label = { Text("# Hashtags") }, enabled = editable)
                AssistChip(onClick = { addToken("@") }, label = { Text("@ Mention") }, enabled = editable)
            }
            HorizontalDivider(color = LoopColors.Divider)
            SoundRow(title = state.sound?.title ?: if (soundId == null) "Original sound" else "Selected sound")
        }
        PostFooter(
            isUploading = state.isUploading,
            progress = state.progress,
            postEnabled = editable,
            onPost = {
                if (navigator.requireAuth()) {
                    focusManager.clearFocus()
                    post()
                }
            },
            onCancel = viewModel::cancelUpload,
        )
    }
}

@Composable
private fun CaptionField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    enabled: Boolean,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 136.dp)
                .focusRequester(focusRequester),
            enabled = enabled,
            placeholder = { Text("Describe your video... #hashtags @mentions") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
        )
        Text(
            text = "${value.text.length}/${ApiLimits.CAPTION_MAX}",
            style = MaterialTheme.typography.labelSmall,
            color = LoopColors.TextMuted,
            modifier = Modifier
                .align(Alignment.End)
                .padding(end = 4.dp),
        )
    }
}

@Composable
private fun SoundRow(title: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PostFooter(
    isUploading: Boolean,
    progress: Float,
    postEnabled: Boolean,
    onPost: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (isUploading) UploadProgress(progress = progress, onCancel = onCancel)
        Button(
            onClick = onPost,
            enabled = postEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = LoopViolet, contentColor = Color.White),
        ) {
            Text("Post", style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun UploadProgress(progress: Float, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = if (progress >= 1f) "Processing..." else "Uploading... ${(progress * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodyMedium,
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = LoopViolet,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
        TextButton(onClick = onCancel) { Text("Cancel") }
    }
}

/** Keeps at most [max] characters, e.g. after a long paste. */
private fun TextFieldValue.limitedTo(max: Int): TextFieldValue {
    if (text.length <= max) return this
    val clipped = text.take(max)
    return TextFieldValue(text = clipped, selection = TextRange(selection.end.coerceAtMost(clipped.length)))
}

/** Appends [symbol], after a space when needed, and moves the cursor behind it. */
private fun TextFieldValue.withToken(symbol: String, max: Int): TextFieldValue {
    val separator = if (text.isEmpty() || text.last().isWhitespace()) "" else " "
    val updated = text + separator + symbol
    if (updated.length > max) return this
    return TextFieldValue(text = updated, selection = TextRange(updated.length))
}
