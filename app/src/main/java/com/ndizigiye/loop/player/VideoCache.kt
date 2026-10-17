package com.ndizigiye.loop.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException

/**
 * One disk cache (512 MB, least recently used first) shared by every player, plus
 * background prefetching of the first bytes of upcoming videos.
 *
 * Create a single instance per process (SimpleCache locks its directory); the
 * [com.ndizigiye.loop.di.AppContainer] owns it.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class VideoCache(context: Context, okHttpClient: OkHttpClient) {

    private val cache = SimpleCache(
        File(context.applicationContext.cacheDir, CACHE_DIR),
        LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
        StandaloneDatabaseProvider(context.applicationContext),
    )

    private val cacheDataSourceFactory: CacheDataSource.Factory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(OkHttpDataSource.Factory(okHttpClient))
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /** Reads through the cache and fills it from the network; use it for every player. */
    val dataSourceFactory: DataSource.Factory get() = cacheDataSourceFactory

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val permits = Semaphore(MAX_CONCURRENT_PREFETCHES)
    private val jobs = mutableMapOf<String, Job>()
    private var released = false

    /**
     * Caches the first [bytes] of [url] in the background so playback can start
     * without waiting for the network. Calls for a URL already being prefetched are
     * ignored; at most a few downloads run at once.
     */
    fun prefetch(url: String, bytes: Long = DEFAULT_PREFETCH_BYTES) {
        synchronized(jobs) {
            if (released || jobs[url]?.isActive == true) return
            val job = scope.launch(start = CoroutineStart.LAZY) {
                permits.withPermit { cacheHead(url, bytes) }
            }
            jobs[url] = job
            job.invokeOnCompletion {
                synchronized(jobs) { if (jobs[url] === job) jobs.remove(url) }
            }
            job.start()
        }
    }

    /** Stops prefetching and closes the cache. The instance must not be used afterwards. */
    fun release() {
        synchronized(jobs) {
            if (released) return
            released = true
            jobs.clear()
        }
        scope.cancel()
        cache.release()
    }

    private suspend fun cacheHead(url: String, bytes: Long) {
        val job = currentCoroutineContext().job
        val dataSpec = DataSpec.Builder().setUri(url).setPosition(0).setLength(bytes).build()
        lateinit var writer: CacheWriter
        // CacheWriter blocks; the progress callback runs between reads, which is where
        // a cancelled coroutine gets the download stopped.
        writer = CacheWriter(cacheDataSourceFactory.createDataSource(), dataSpec, null) { _, _, _ ->
            if (!job.isActive) writer.cancel()
        }
        try {
            writer.cache()
        } catch (e: IOException) {
            // Prefetching is best effort; playback fetches whatever is missing.
        }
    }

    private companion object {
        const val CACHE_DIR = "video-cache"
        const val MAX_CACHE_BYTES = 512L * 1024 * 1024
        const val DEFAULT_PREFETCH_BYTES = 1_500_000L
        const val MAX_CONCURRENT_PREFETCHES = 3
    }
}
