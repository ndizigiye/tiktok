package com.ndizigiye.loop.ui.comments

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** Confirms deleting a comment; [withReplies] mentions that its replies go too. */
@Composable
internal fun DeleteCommentDialog(
    withReplies: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete comment?") },
        text = {
            Text(
                if (withReplies) {
                    "The comment and its replies will be removed for everyone."
                } else {
                    "The comment will be removed for everyone."
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}
