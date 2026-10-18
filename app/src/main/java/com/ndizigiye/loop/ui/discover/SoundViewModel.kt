package com.ndizigiye.loop.ui.discover

import com.ndizigiye.loop.api.SoundDto
import com.ndizigiye.loop.data.DiscoverRepository
import com.ndizigiye.loop.data.VideoRepository

/** Sound [soundId]: its details and the videos using it, most viewed first. */
class SoundViewModel(
    val soundId: Long,
    discoverRepository: DiscoverRepository,
    videoRepository: VideoRepository,
) : VideoCollectionViewModel<SoundDto>(
    loadHeader = { discoverRepository.sound(soundId) },
    loadPage = { cursor -> videoRepository.soundVideos(soundId, cursor) },
)
