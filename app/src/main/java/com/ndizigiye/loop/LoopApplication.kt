package com.ndizigiye.loop

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.ndizigiye.loop.data.deleteStaleTempFiles
import com.ndizigiye.loop.di.AppContainer
import kotlinx.coroutines.launch

class LoopApplication : Application(), SingletonImageLoader.Factory {

    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Takes and upload copies of a post screen abandoned at process death are never cleaned up otherwise.
        container.applicationScope.launch { deleteStaleTempFiles(maxAgeMs = STALE_TEMP_FILE_AGE_MS) }
    }

    /** Coil loads avatars and thumbnails through the app's OkHttpClient. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { container.okHttpClient }))
            }
            .crossfade(true)
            .build()

    private companion object {
        const val STALE_TEMP_FILE_AGE_MS = 24L * 60 * 60 * 1000
    }
}
