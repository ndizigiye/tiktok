package com.ndizigiye.loop.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ndizigiye.loop.api.ApiLimits
import com.ndizigiye.loop.api.UserDto
import com.ndizigiye.loop.ui.common.Avatar
import com.ndizigiye.loop.ui.common.LocalSnackbarHostState
import com.ndizigiye.loop.ui.common.LoopTopBar
import com.ndizigiye.loop.ui.common.SignedOutPrompt
import com.ndizigiye.loop.ui.common.rememberBackAction
import com.ndizigiye.loop.ui.common.rememberMessageScope
import com.ndizigiye.loop.ui.navigation.LocalAppContainer
import com.ndizigiye.loop.ui.theme.LoopColors
import kotlinx.coroutines.launch

private val AvatarSize = 96.dp

/** Edits the signed-in user's photo, name and bio. */
@Composable
fun EditProfileScreen() {
    val container = LocalAppContainer.current
    val session by container.sessionStore.session.collectAsStateWithLifecycle()
    val user = session?.user
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (user == null) {
            LoopTopBar(title = "Edit profile")
            SignedOutPrompt(title = "Edit profile", subtitle = "Log in to edit your profile.")
        } else {
            EditProfileForm(user = user)
        }
    }
}

@Composable
private fun EditProfileForm(user: UserDto) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val snackbarHostState = LocalSnackbarHostState.current
    val viewModel: EditProfileViewModel = viewModel { EditProfileViewModel(container.userRepository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf(user.displayName) }
    var bio by rememberSaveable { mutableStateOf(user.bio) }
    val back = rememberBackAction()
    val messageScope = rememberMessageScope()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                EditProfileEvent.Saved -> {
                    // The snackbar outlives this screen, which closes right away.
                    messageScope.launch { snackbarHostState.showSnackbar("Profile updated") }
                    back()
                }
                EditProfileEvent.AvatarUpdated -> launch { snackbarHostState.showSnackbar("Profile photo updated") }
                is EditProfileEvent.Error -> launch { snackbarHostState.showSnackbar(event.message) }
            }
        }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.uploadAvatar(context, uri)
    }
    val onPickAvatar = {
        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    // Input is capped at the limits as it is typed, so only an empty name can be invalid.
    val nameValid = name.isNotBlank()
    val changed = name.trim() != user.displayName || bio.trim() != user.bio
    val canSave = changed && nameValid && !state.isSaving

    LoopTopBar(
        title = "Edit profile",
        actions = {
            if (state.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .size(20.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                )
            } else {
                TextButton(onClick = { viewModel.save(user, name, bio) }, enabled = canSave) {
                    Text("Save")
                }
            }
        },
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AvatarEditor(user = user, isUploading = state.isUploadingAvatar, onPick = onPickAvatar)
        Spacer(Modifier.size(4.dp))
        OutlinedTextField(
            value = user.username,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            enabled = false,
            label = { Text("Username") },
            prefix = { Text("@") },
            supportingText = { Text("Usernames can't be changed.") },
            singleLine = true,
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.replace('\n', ' ').take(ApiLimits.DISPLAY_NAME_MAX) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Name") },
            supportingText = {
                FieldSupport(
                    message = if (nameValid) null else "Enter a name",
                    length = name.length,
                    max = ApiLimits.DISPLAY_NAME_MAX,
                )
            },
            isError = !nameValid,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
            ),
            singleLine = true,
        )
        OutlinedTextField(
            value = bio,
            onValueChange = { bio = it.take(ApiLimits.BIO_MAX) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Bio") },
            placeholder = { Text("Tell people a little about yourself") },
            supportingText = { FieldSupport(message = null, length = bio.length, max = ApiLimits.BIO_MAX) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            minLines = 3,
            maxLines = 6,
        )
    }
}

@Composable
private fun AvatarEditor(user: UserDto, isUploading: Boolean, onPick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(AvatarSize)) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(CircleShape)
                    .clickable(
                        enabled = !isUploading,
                        onClickLabel = "Change photo",
                        role = Role.Button,
                        onClick = onPick,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Avatar(user = user, size = AvatarSize)
                if (isUploading) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(LoopColors.OverlayScrim),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = Color.White,
                            strokeWidth = 3.dp,
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PhotoCamera,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        TextButton(onClick = onPick, enabled = !isUploading) {
            Text(if (isUploading) "Uploading photo…" else "Change photo")
        }
    }
}

/** Supporting line of a form field: an optional [message] and a "length/max" counter. */
@Composable
private fun FieldSupport(message: String?, length: Int, max: Int) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(text = message.orEmpty(), modifier = Modifier.weight(1f))
        Text(text = "$length/$max", textAlign = TextAlign.End)
    }
}
