package com.ndizigiye.loop.data.remote

import com.ndizigiye.loop.api.AuthResponse
import com.ndizigiye.loop.api.CommentDto
import com.ndizigiye.loop.api.CountDto
import com.ndizigiye.loop.api.CreateCommentRequest
import com.ndizigiye.loop.api.HashtagDto
import com.ndizigiye.loop.api.LoginRequest
import com.ndizigiye.loop.api.NotificationDto
import com.ndizigiye.loop.api.PageDto
import com.ndizigiye.loop.api.RegisterRequest
import com.ndizigiye.loop.api.SearchResultDto
import com.ndizigiye.loop.api.SoundDto
import com.ndizigiye.loop.api.ToggleResultDto
import com.ndizigiye.loop.api.TrendingDto
import com.ndizigiye.loop.api.UpdateProfileRequest
import com.ndizigiye.loop.api.UserDto
import com.ndizigiye.loop.api.VideoDto
import com.ndizigiye.loop.api.ViewEventRequest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * Retrofit description of every endpoint in docs/api.md. Paths are relative to the
 * placeholder base URL; [BaseUrlInterceptor] points them at the configured server.
 * Endpoints answering `204 No Content` return [Unit].
 */
interface LoopApi {

    // ---- Health

    /** `GET /health` on the configured server. */
    @GET("health")
    suspend fun health(): ResponseBody

    /** `GET` an absolute URL such as `http://192.168.1.20:8080/health`, bypassing the configured server. */
    @GET
    suspend fun healthAt(@Url url: String): ResponseBody

    // ---- Auth and current user

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @GET("api/me")
    suspend fun me(): UserDto

    @PATCH("api/me")
    suspend fun updateMe(@Body request: UpdateProfileRequest): UserDto

    @Multipart
    @POST("api/me/avatar")
    suspend fun uploadAvatar(@Part avatar: MultipartBody.Part): UserDto

    @GET("api/me/bookmarks")
    suspend fun bookmarks(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): PageDto<VideoDto>

    // ---- Feeds

    @GET("api/feed/for-you")
    suspend fun forYouFeed(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): PageDto<VideoDto>

    @GET("api/feed/following")
    suspend fun followingFeed(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): PageDto<VideoDto>

    // ---- Videos

    /** Multipart upload: file part `video`, form field `caption`, optional form field `soundId`. */
    @Multipart
    @POST("api/videos")
    suspend fun uploadVideo(
        @Part video: MultipartBody.Part,
        @Part("caption") caption: RequestBody,
        @Part("soundId") soundId: RequestBody?,
    ): VideoDto

    @GET("api/videos/{id}")
    suspend fun video(@Path("id") id: Long): VideoDto

    @DELETE("api/videos/{id}")
    suspend fun deleteVideo(@Path("id") id: Long): Unit

    @PUT("api/videos/{id}/like")
    suspend fun likeVideo(@Path("id") id: Long): ToggleResultDto

    @DELETE("api/videos/{id}/like")
    suspend fun unlikeVideo(@Path("id") id: Long): ToggleResultDto

    @PUT("api/videos/{id}/bookmark")
    suspend fun bookmarkVideo(@Path("id") id: Long): ToggleResultDto

    @DELETE("api/videos/{id}/bookmark")
    suspend fun unbookmarkVideo(@Path("id") id: Long): ToggleResultDto

    @POST("api/videos/{id}/share")
    suspend fun shareVideo(@Path("id") id: Long): CountDto

    @POST("api/videos/{id}/views")
    suspend fun recordView(@Path("id") id: Long, @Body request: ViewEventRequest): Unit

    @POST("api/videos/{id}/not-interested")
    suspend fun notInterested(@Path("id") id: Long): Unit

    // ---- Comments

    @GET("api/videos/{id}/comments")
    suspend fun comments(
        @Path("id") videoId: Long,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): PageDto<CommentDto>

    @GET("api/comments/{id}/replies")
    suspend fun replies(
        @Path("id") commentId: Long,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): PageDto<CommentDto>

    @POST("api/videos/{id}/comments")
    suspend fun postComment(@Path("id") videoId: Long, @Body request: CreateCommentRequest): CommentDto

    @DELETE("api/comments/{id}")
    suspend fun deleteComment(@Path("id") commentId: Long): Unit

    @PUT("api/comments/{id}/like")
    suspend fun likeComment(@Path("id") commentId: Long): ToggleResultDto

    @DELETE("api/comments/{id}/like")
    suspend fun unlikeComment(@Path("id") commentId: Long): ToggleResultDto

    // ---- Users

    @GET("api/users/{username}")
    suspend fun user(@Path("username") username: String): UserDto

    @GET("api/users/{username}/videos")
    suspend fun userVideos(
        @Path("username") username: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): PageDto<VideoDto>

    @GET("api/users/{username}/liked")
    suspend fun likedVideos(
        @Path("username") username: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): PageDto<VideoDto>

    @GET("api/users/{username}/followers")
    suspend fun followers(
        @Path("username") username: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): PageDto<UserDto>

    @GET("api/users/{username}/following")
    suspend fun following(
        @Path("username") username: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): PageDto<UserDto>

    @PUT("api/users/{username}/follow")
    suspend fun follow(@Path("username") username: String): ToggleResultDto

    @DELETE("api/users/{username}/follow")
    suspend fun unfollow(@Path("username") username: String): ToggleResultDto

    // ---- Discover and search

    @GET("api/discover")
    suspend fun discover(): TrendingDto

    @GET("api/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("limit") limit: Int? = null,
    ): SearchResultDto

    @GET("api/hashtags/{name}")
    suspend fun hashtag(@Path("name") name: String): HashtagDto

    @GET("api/hashtags/{name}/videos")
    suspend fun hashtagVideos(
        @Path("name") name: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): PageDto<VideoDto>

    @GET("api/sounds/{id}")
    suspend fun sound(@Path("id") id: Long): SoundDto

    @GET("api/sounds/{id}/videos")
    suspend fun soundVideos(
        @Path("id") id: Long,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): PageDto<VideoDto>

    // ---- Notifications

    @GET("api/notifications")
    suspend fun notifications(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): PageDto<NotificationDto>

    @GET("api/notifications/unread-count")
    suspend fun unreadCount(): CountDto

    @POST("api/notifications/read")
    suspend fun markNotificationsRead(): Unit
}
