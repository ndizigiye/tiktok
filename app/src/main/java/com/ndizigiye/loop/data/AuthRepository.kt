package com.ndizigiye.loop.data

import com.ndizigiye.loop.api.AuthResponse
import com.ndizigiye.loop.api.LoginRequest
import com.ndizigiye.loop.api.RegisterRequest
import com.ndizigiye.loop.api.UserDto
import com.ndizigiye.loop.data.remote.LoopApi
import com.ndizigiye.loop.data.remote.apiCall
import com.ndizigiye.loop.data.session.SessionStore

class AuthRepository(
    private val api: LoopApi,
    private val sessionStore: SessionStore,
    private val interactions: InteractionStore,
    private val inboxRepository: InboxRepository,
    private val commentRepository: CommentRepository,
) {
    suspend fun login(username: String, password: String): Result<UserDto> =
        apiCall { api.login(LoginRequest(username.trim().lowercase(), password)) }
            .map(::startSession)

    suspend fun register(username: String, displayName: String, password: String): Result<UserDto> =
        apiCall { api.register(RegisterRequest(username.trim().lowercase(), displayName.trim(), password)) }
            .map(::startSession)

    /** Reloads the signed-in user's profile into [SessionStore]. */
    suspend fun refreshMe(): Result<UserDto> =
        apiCall { api.me() }.onSuccess(sessionStore::updateUser)

    /** Signs out locally: clears the session, interaction state, prefetched comments and unread count. */
    fun logout() {
        sessionStore.clear()
        interactions.clear()
        commentRepository.clearPrefetched()
        inboxRepository.reset()
    }

    private fun startSession(auth: AuthResponse): UserDto {
        // Like/follow flags loaded as another viewer are stale for the new account.
        interactions.clear()
        commentRepository.clearPrefetched()
        inboxRepository.reset()
        sessionStore.save(auth.token, auth.user)
        return auth.user
    }
}
