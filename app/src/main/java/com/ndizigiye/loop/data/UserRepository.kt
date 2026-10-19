package com.ndizigiye.loop.data

import com.ndizigiye.loop.api.PageDto
import com.ndizigiye.loop.api.ToggleResultDto
import com.ndizigiye.loop.api.UpdateProfileRequest
import com.ndizigiye.loop.api.UserDto
import com.ndizigiye.loop.data.remote.LoopApi
import com.ndizigiye.loop.data.remote.apiCall
import com.ndizigiye.loop.data.session.SessionStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class UserRepository(
    private val api: LoopApi,
    private val sessionStore: SessionStore,
    private val interactions: InteractionStore,
) {
    suspend fun user(username: String): Result<UserDto> =
        apiCall { api.user(username) }.onSuccess { user ->
            interactions.rememberUsers(listOf(user))
            if (user.isMe) sessionStore.updateUser(user)
        }

    /**
     * Optimistically follows/unfollows [user] in [InteractionStore]; reverts on failure.
     * On success the signed-in user's following count is adjusted too.
     */
    suspend fun setFollowing(user: UserDto, follow: Boolean): Result<ToggleResultDto> =
        withContext(NonCancellable) {
            val before = interactions.isFollowing(user)
            interactions.beginFollowMutation(user.id)
            try {
                interactions.setFollowing(user.id, follow)
                apiCall { if (follow) api.follow(user.username) else api.unfollow(user.username) }
                    .onSuccess { result ->
                        interactions.setFollowing(user.id, result.active)
                        if (result.active != before) adjustMyFollowingCount(if (result.active) 1 else -1)
                    }
                    .onFailure { interactions.setFollowing(user.id, before) }
            } finally {
                interactions.endFollowMutation(user.id)
            }
        }

    suspend fun followers(username: String, cursor: String?): Result<PageDto<UserDto>> =
        apiCall { api.followers(username, cursor) }.onSuccess { interactions.rememberUsers(it.items) }

    suspend fun following(username: String, cursor: String?): Result<PageDto<UserDto>> =
        apiCall { api.following(username, cursor) }.onSuccess { interactions.rememberUsers(it.items) }

    /** Updates the signed-in profile; null fields stay unchanged. Also updates [SessionStore]. */
    suspend fun updateProfile(displayName: String?, bio: String?): Result<UserDto> =
        apiCall { api.updateMe(UpdateProfileRequest(displayName?.trim(), bio?.trim())) }
            .onSuccess(sessionStore::updateUser)

    /** Uploads a JPEG/PNG/WebP avatar. Also updates [SessionStore]. */
    suspend fun uploadAvatar(file: File, mimeType: String): Result<UserDto> {
        val part = MultipartBody.Part.createFormData(
            "avatar",
            file.name,
            file.asRequestBody(mimeType.toMediaTypeOrNull()),
        )
        return apiCall { api.uploadAvatar(part) }.onSuccess(sessionStore::updateUser)
    }

    private fun adjustMyFollowingCount(delta: Int) {
        val me = sessionStore.currentUser ?: return
        sessionStore.updateUser(me.copy(followingCount = (me.followingCount + delta).coerceAtLeast(0)))
    }
}
