package com.ndizigiye.loop.server.media

import org.slf4j.LoggerFactory
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Local working files: uploads while they are received, probed and thumbnailed, and demo media
 * while it renders. They live in `<data dir>/tmp`, so [LocalMediaStore] moves them into place with
 * a rename; with Supabase Storage they are uploaded and deleted. Whoever creates a file deletes
 * it; files a crash left behind are removed at the next start.
 */
class MediaStaging(private val dir: File, staleAfter: Duration = STALE_AFTER) {
    init {
        dir.mkdirs()
        sweep(staleAfter)
    }

    /** A new empty file ending in `.<extension>`, with default permissions like any media file. */
    fun newFile(extension: String): File = File.createTempFile("loop-", ".$extension", dir)

    /** Files still in the staging directory (tests check that requests leave none behind). */
    fun files(): List<File> = dir.listFiles()?.filter { it.isFile }.orEmpty()

    private fun sweep(staleAfter: Duration) {
        val cutoff = System.currentTimeMillis() - staleAfter.inWholeMilliseconds
        val stale = files().filter { it.lastModified() < cutoff }
        stale.forEach { it.delete() }
        if (stale.isNotEmpty()) {
            LoggerFactory.getLogger(MediaStaging::class.java).info("Deleted {} stale file(s) from {}", stale.size, dir)
        }
    }

    private companion object {
        /** Older than any upload or render in progress, so a second process sharing the directory is safe. */
        val STALE_AFTER = 6.hours
    }
}
