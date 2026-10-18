package com.ndizigiye.loop.di

import android.content.Context
import com.ndizigiye.loop.BuildConfig
import com.ndizigiye.loop.data.AuthRepository
import com.ndizigiye.loop.data.CommentRepository
import com.ndizigiye.loop.data.DiscoverRepository
import com.ndizigiye.loop.data.InboxRepository
import com.ndizigiye.loop.data.InteractionStore
import com.ndizigiye.loop.data.UserRepository
import com.ndizigiye.loop.data.VideoListHandoff
import com.ndizigiye.loop.data.VideoRepository
import com.ndizigiye.loop.data.remote.AuthInterceptor
import com.ndizigiye.loop.data.remote.BaseUrlInterceptor
import com.ndizigiye.loop.data.remote.LoopApi
import com.ndizigiye.loop.data.remote.LoopJson
import com.ndizigiye.loop.data.session.SessionStore
import com.ndizigiye.loop.data.upload.UploadManager
import com.ndizigiye.loop.player.VideoCache
import com.ndizigiye.loop.ui.navigation.MainTab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/** Hand-written dependency graph; one instance per process, owned by LoopApplication. */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /**
     * Lives as long as the process: for work that must finish even when the screen or
     * ViewModel that started it goes away, such as view reports and share counts.
     * Snackbars that outlive a screen use `rememberMessageScope()` in ui.common instead.
     */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val sessionStore: SessionStore by lazy { SessionStore(appContext) }

    /** Shared by Retrofit, Coil and Media3. Only Loop-server requests get the auth header. */
    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(sessionStore) { authRepository.logout() })
            .addInterceptor(BaseUrlInterceptor(sessionStore))
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
                }
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    val api: LoopApi by lazy {
        Retrofit.Builder()
            .baseUrl(BaseUrlInterceptor.PLACEHOLDER_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(LoopJson.asConverterFactory("application/json; charset=UTF-8".toMediaType()))
            .build()
            .create(LoopApi::class.java)
    }

    val interactions: InteractionStore by lazy { InteractionStore() }

    val inboxRepository: InboxRepository by lazy { InboxRepository(api, sessionStore, interactions) }

    val authRepository: AuthRepository by lazy {
        AuthRepository(api, sessionStore, interactions, inboxRepository)
    }

    val videoRepository: VideoRepository by lazy { VideoRepository(api, interactions) }

    val userRepository: UserRepository by lazy { UserRepository(api, sessionStore, interactions) }

    val commentRepository: CommentRepository by lazy { CommentRepository(api, interactions) }

    val discoverRepository: DiscoverRepository by lazy { DiscoverRepository(api, interactions) }

    /** Video uploads; they run in [applicationScope] under a foreground service, outliving the post screen. */
    val uploadManager: UploadManager by lazy { UploadManager(appContext, videoRepository, applicationScope) }

    val videoCache: VideoCache by lazy { VideoCache(appContext, okHttpClient) }

    val videoListHandoff: VideoListHandoff = VideoListHandoff()

    /** Selected bottom tab of the main shell; survives navigation to other destinations. */
    val mainTab: MutableStateFlow<MainTab> = MutableStateFlow(MainTab.HOME)
}
