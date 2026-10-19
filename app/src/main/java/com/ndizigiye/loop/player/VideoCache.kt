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
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException

/**
 * One disk cache (512 MB, least recently used first) shared by every player, plus
 * background prefetching of the first bytes of upcoming videos (scheduled by
 * [prefetchWhileBuffered]).
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
    private val jobs = mutableMapOf<String, PrefetchJob>()
    private var released = false

    /**
     * Caches the first [bytes] of [url] in the background so playback can start
     * without waiting for the network. A call for a URL already being prefetched at
     * least as far is ignored; a smaller prefetch of it is replaced by this one. At
     * most a few downloads run at once.
     */
    fun prefetch(url: String, bytes: Long) {
        synchronized(jobs) {
            if (released) return
            val running = jobs[url]?.takeIf { it.job.isActive }
            if (running != null && running.bytes >= bytes) return
            val job = scope.launch(start = CoroutineStart.LAZY) {
                // Let the smaller download stop first; what it wrote stays cached and is skipped.
                running?.job?.cancelAndJoin()
                permits.withPermit { cacheHead(url, bytes) }
            }
            jobs[url] = PrefetchJob(bytes, job)
            job.invokeOnCompletion {
                synchronized(jobs) { if (jobs[url]?.job === job) jobs.remove(url) }
            }
            job.start()
        }
    }

    /**
     * Stops every prefetch in flight, e.g. so the video on screen gets the bandwidth.
     * Bytes already written stay cached, and a later [prefetch] continues after them.
     */
    fun cancelPrefetches() {
        val running = synchronized(jobs) { jobs.values.map { it.job } }
        running.forEach { it.cancel() }
    }

    /** Stops the prefetch of [url] if one is in flight, like [cancelPrefetches] does for all. */
    fun cancelPrefetch(url: String) {
        val running = synchronized(jobs) { jobs[url]?.job }
        running?.cancel()
    }

    /**
     * Bytes of video currently on disk. Runs on the IO dispatcher.
     * @throws IllegalStateException after [release].
     */
    suspend fun usedBytes(): Long = withContext(Dispatchers.IO) { cache.cacheSpace }

    /**
     * Removes every cached video. Runs on the IO dispatcher. A player or prefetch
     * reading a removed video only goes back to the network for it.
     * @throws IllegalStateException after [release].
     */
    suspend fun clear() {
        withContext(Dispatchers.IO) {
            for (key in cache.keys) cache.removeResource(key)
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

    private class PrefetchJob(val bytes: Long, val job: Job)

    private companion object {
        const val CACHE_DIR = "video-cache"
        const val MAX_CACHE_BYTES = 512L * 1024 * 1024
        const val MAX_CONCURRENT_PREFETCHES = 3
    }
}
