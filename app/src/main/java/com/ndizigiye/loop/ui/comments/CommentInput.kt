package com.ndizigiye.loop.ui.comments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ndizigiye.loop.api.ApiLimits
import com.ndizigiye.loop.api.CommentDto
import com.ndizigiye.loop.api.UserDto
import com.ndizigiye.loop.ui.common.Avatar
import com.ndizigiye.loop.ui.theme.LoopColors

private const val PLACEHOLDER = "Add comment..."
private val FieldShape = RoundedCornerShape(20.dp)
private val InputAvatarSize = 32.dp

/**
 * The composer pinned under the comment list. Signed out ([me] is null) it shows a
 * look-alike field that calls [onSignedOutTap]. Pads for the keyboard and the
 * navigation bar itself.
 */
@Composable
internal fun CommentInput(
    me: UserDto?,
    text: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    replyTarget: CommentDto?,
    onCancelReply: () -> Unit,
    isPosting: Boolean,
    onSend: () -> Unit,
    onSignedOutTap: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val canSend = me != null && text.text.isNotBlank() && !isPosting
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .imePadding()
            .navigationBarsPadding(),
    ) {
        HorizontalDivider(thickness = 0.5.dp, color = LoopColors.Divider)
        if (replyTarget != null) {
            ReplyingToRow(username = replyTarget.author.username, onCancel = onCancelReply)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (me != null) {
                Avatar(user = me, size = InputAvatarSize)
            } else {
                Box(
                    modifier = Modifier
                        .size(InputAvatarSize)
                        .clip(CircleShape)
                        .background(LoopColors.Placeholder),
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            if (me != null) {
                CommentField(
                    text = text,
                    onTextChange = onTextChange,
                    onSend = { if (canSend) onSend() },
                    focusRequester = focusRequester,
                    modifier = Modifier.weight(1f),
                )
            } else {
                SignedOutField(onClick = onSignedOutTap, modifier = Modifier.weight(1f))
            }
            IconButton(onClick = onSend, enabled = canSend) {
                if (isPosting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Post comment",
                        tint = if (canSend) MaterialTheme.colorScheme.primary else LoopColors.TextMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReplyingToRow(username: String, onCancel: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Replying to @$username",
            style = MaterialTheme.typography.bodySmall,
            color = LoopColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onCancel, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Cancel reply",
                tint = LoopColors.TextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun CommentField(
    text: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = text,
        onValueChange = { onTextChange(it.copy(text = it.text.take(ApiLimits.COMMENT_MAX))) },
        modifier = modifier.focusRequester(focusRequester),
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Send,
        ),
        keyboardActions = KeyboardActions(onSend = { onSend() }),
        maxLines = 4,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            FieldFrame {
                if (text.text.isEmpty()) {
                    Text(text = PLACEHOLDER, style = MaterialTheme.typography.bodyMedium, color = LoopColors.TextMuted)
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun SignedOutField(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FieldFrame(
        modifier = modifier
            .clip(FieldShape)
            .clickable(role = Role.Button, onClickLabel = "Log in to comment", onClick = onClick),
    ) {
        Text(text = PLACEHOLDER, style = MaterialTheme.typography.bodyMedium, color = LoopColors.TextMuted)
    }
}

/** The rounded, filled frame shared by the real field and its signed-out stand-in. */
@Composable
private fun FieldFrame(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, FieldShape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        content()
    }
}
