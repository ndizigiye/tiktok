package com.ndizigiye.loop.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ndizigiye.loop.api.HashtagDto
import com.ndizigiye.loop.api.SoundDto
import com.ndizigiye.loop.api.TrendingHashtagDto
import com.ndizigiye.loop.api.UserDto
import com.ndizigiye.loop.ui.common.Avatar
import com.ndizigiye.loop.ui.common.FollowButton
import com.ndizigiye.loop.ui.common.VideoThumbnailCard
import com.ndizigiye.loop.ui.theme.LoopColors

private val ThumbnailWidth = 110.dp
private val ThumbnailShape = RoundedCornerShape(8.dp)

@Composable
internal fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 10.dp),
    )
}

/** Horizontally scrolling cards of suggested creators. */
@Composable
internal fun CreatorRow(
    creators: List<UserDto>,
    onCreatorClick: (UserDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keys = remember(creators) { uniqueKeys("creator", creators.map { it.id }) }
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(count = creators.size, key = { index -> keys[index] }, contentType = { "creator" }) { index ->
            val creator = creators[index]
            CreatorCard(user = creator, onClick = { onCreatorClick(creator) })
        }
    }
}

@Composable
private fun CreatorCard(user: UserDto, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .width(140.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClickLabel = "Open profile", role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Avatar(user = user, size = 64.dp)
        Spacer(Modifier.height(10.dp))
        Text(
            text = user.displayName,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "@${user.username}",
            style = MaterialTheme.typography.bodySmall,
            color = LoopColors.TextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        FollowButton(user = user, compact = true)
    }
}

/** A trending hashtag: a header row opening its page, then a strip of its top videos. */
@Composable
internal fun TrendingHashtagSection(
    trending: TrendingHashtagDto,
    onHeaderClick: () -> Unit,
    onVideoClick: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val videos = trending.videos
    Column(modifier = modifier.fillMaxWidth().padding(top = 8.dp)) {
        TrendingHashtagHeader(hashtag = trending.hashtag, onClick = onHeaderClick)
        if (videos.isNotEmpty()) {
            val keys = remember(videos) { uniqueKeys("v", videos.map { it.id }) }
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(count = videos.size, key = { index -> keys[index] }, contentType = { "video" }) { index ->
                    VideoThumbnailCard(
                        video = videos[index],
                        modifier = Modifier
                            .width(ThumbnailWidth)
                            .clip(ThumbnailShape),
                        onClick = { onVideoClick(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TrendingHashtagHeader(hashtag: HashtagDto, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Open hashtag", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HashtagBadge(size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "#${hashtag.name}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = viewsLabel(hashtag.viewCount),
                style = MaterialTheme.typography.bodySmall,
                color = LoopColors.TextSecondary,
                maxLines = 1,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = LoopColors.TextMuted,
        )
    }
}

/** A trending sound: its disc, title, author and how many videos use it. */
@Composable
internal fun TrendingSoundRow(sound: SoundDto, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Open sound", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SoundCoverDisc(coverUrl = sound.coverUrl, size = 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = sound.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = sound.authorName,
                style = MaterialTheme.typography.bodySmall,
                color = LoopColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = videosLabel(sound.videoCount),
            style = MaterialTheme.typography.labelMedium,
            color = LoopColors.TextMuted,
            maxLines = 1,
        )
    }
}
