package com.ndizigiye.loop.ui.discover

import com.ndizigiye.loop.api.HashtagDto
import com.ndizigiye.loop.data.DiscoverRepository
import com.ndizigiye.loop.data.VideoRepository

/** Hashtag [name] (lowercase, without '#'): its totals and its videos, most viewed first. */
class HashtagViewModel(
    val name: String,
    discoverRepository: DiscoverRepository,
    videoRepository: VideoRepository,
) : VideoCollectionViewModel<HashtagDto>(
    loadHeader = { discoverRepository.hashtag(name) },
    loadPage = { cursor -> videoRepository.hashtagVideos(name, cursor) },
)
