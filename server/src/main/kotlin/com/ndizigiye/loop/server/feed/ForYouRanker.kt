package com.ndizigiye.loop.server.feed

/** Engagement and metadata of one candidate video. */
data class Candidate(
    val id: Long,
    val authorId: Long,
    val createdAt: Long,
    val likes: Long = 0,
    val comments: Long = 0,
    val shares: Long = 0,
    val bookmarks: Long = 0,
    val completions: Long = 0,
    val views: Long = 0,
    val hashtagIds: Set<Long> = emptySet(),
)

/** What the ranking knows about the viewer; empty for anonymous viewers. */
data class ViewerSignals(
    val viewerId: Long? = null,
    val followedAuthors: Set<Long> = emptySet(),
    val likedHashtagIds: Set<Long> = emptySet(),
    val watchedVideoIds: Set<Long> = emptySet(),
)

/** The For You heuristic from docs/api.md ("Ranking"). Pure and deterministic for a given seed. */
object ForYouRanker {
    private const val FRESHNESS_HALF_LIFE_HOURS = 48.0
    private const val FOLLOW_BOOST = 0.6
    private const val HASHTAG_AFFINITY_WEIGHT = 0.4
    private const val WATCHED_PENALTY = 0.8
    private const val JITTER_RANGE = 0.3
    private const val MILLIS_PER_HOUR = 3_600_000.0

    /**
     * Candidates ordered by score, then reordered so that no two consecutive videos share an
     * author when another choice exists. [avoidFirstAuthor] continues that rule across passes.
     */
    fun rank(
        candidates: List<Candidate>,
        viewer: ViewerSignals,
        seed: Long,
        now: Long,
        avoidFirstAuthor: Long? = null,
    ): List<Candidate> {
        val sorted = candidates
            .map { it to score(it, viewer, seed, now) }
            .sortedWith(compareByDescending<Pair<Candidate, Double>> { it.second }.thenBy { it.first.id })
            .map { it.first }
        return diversify(sorted, avoidFirstAuthor)
    }

    fun score(video: Candidate, viewer: ViewerSignals, seed: Long, now: Long): Double {
        val engagement = (video.likes + 2.0 * video.comments + 3.0 * video.shares + 2.0 * video.bookmarks + video.completions) /
            (video.views + 10.0)
        val ageHours = ((now - video.createdAt) / MILLIS_PER_HOUR).coerceAtLeast(0.0)
        val freshness = 1.0 / (1.0 + ageHours / FRESHNESS_HALF_LIFE_HOURS)
        val followBoost = if (video.authorId in viewer.followedAuthors) FOLLOW_BOOST else 0.0
        val affinity = if (video.hashtagIds.isEmpty()) {
            0.0
        } else {
            video.hashtagIds.count { it in viewer.likedHashtagIds }.toDouble() / video.hashtagIds.size
        }
        val watchedPenalty = if (video.id in viewer.watchedVideoIds) WATCHED_PENALTY else 0.0
        return engagement * freshness +
            followBoost +
            HASHTAG_AFFINITY_WEIGHT * affinity -
            watchedPenalty +
            jitter(viewer.viewerId ?: 0, seed, video.id)
    }

    /** Exploration noise in [0, 0.3), a pure function of (viewer, seed, video). */
    fun jitter(viewerId: Long, seed: Long, videoId: Long): Double {
        val hash = mix64(mix64(mix64(seed) xor viewerId) xor videoId)
        return (hash ushr 11).toDouble() / (1L shl 53).toDouble() * JITTER_RANGE
    }

    /** Seed of the pass that follows the one ranked with [seed]. */
    fun nextSeed(seed: Long): Long = mix64(seed + GOLDEN_GAMMA)

    /**
     * Takes the best-scoring video whose author differs from the previous one and still leaves
     * an order of the rest without repeats; repeats only happen when no such order exists.
     */
    private fun diversify(sorted: List<Candidate>, avoidFirstAuthor: Long?): List<Candidate> {
        val remaining = sorted.toMutableList()
        val counts = sorted.groupingBy { it.authorId }.eachCountTo(HashMap())
        val result = ArrayList<Candidate>(sorted.size)
        var previousAuthor = avoidFirstAuthor
        while (remaining.isNotEmpty()) {
            val feasible = feasibilityAfterPick(counts, rest = remaining.size - 1)
            val index = remaining.indexOfFirst { it.authorId != previousAuthor && feasible(it.authorId) }
                .takeIf { it >= 0 }
                ?: remaining.indexOfFirst { it.authorId != previousAuthor }.coerceAtLeast(0)
            val next = remaining.removeAt(index)
            result += next
            counts.merge(next.authorId, -1, Int::plus)
            previousAuthor = next.authorId
        }
        return result
    }

    /**
     * Whether, after taking one video by an author, the [rest] can be ordered without two
     * adjacent videos by the same author: that author may fill at most every other slot
     * after the first, everyone else at most every other slot.
     */
    private fun feasibilityAfterPick(counts: Map<Long, Int>, rest: Int): (Long) -> Boolean {
        var topAuthor: Long? = null
        var top = 0
        var second = 0
        for ((author, count) in counts) {
            if (count > top) {
                second = top
                top = count
                topAuthor = author
            } else if (count > second) {
                second = count
            }
        }
        return { author ->
            val maxOther = if (author == topAuthor) second else top
            counts.getValue(author) - 1 <= rest / 2 && maxOther <= (rest + 1) / 2
        }
    }

    /** SplitMix64 finaliser: a well-mixed 64-bit hash of [value]. */
    private fun mix64(value: Long): Long {
        var z = value + GOLDEN_GAMMA
        z = (z xor (z ushr 30)) * MIX_1
        z = (z xor (z ushr 27)) * MIX_2
        return z xor (z ushr 31)
    }

    private val GOLDEN_GAMMA = 0x9E3779B97F4A7C15uL.toLong()
    private val MIX_1 = 0xBF58476D1CE4E5B9uL.toLong()
    private val MIX_2 = 0x94D049BB133111EBuL.toLong()
}
