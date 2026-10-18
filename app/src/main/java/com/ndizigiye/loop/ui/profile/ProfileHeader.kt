package com.ndizigiye.loop.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ndizigiye.loop.api.UserDto
import com.ndizigiye.loop.common.formatCount
import com.ndizigiye.loop.ui.common.Avatar
import com.ndizigiye.loop.ui.common.CaptionText
import com.ndizigiye.loop.ui.common.FollowButton
import com.ndizigiye.loop.ui.common.rememberIsFollowing
import com.ndizigiye.loop.ui.theme.LoopColors

/** Avatar, names, counts, actions and bio of a profile. */
@Composable
internal fun ProfileHeader(
    user: UserDto,
    isOwn: Boolean,
    onFollowingClick: () -> Unit,
    onFollowersClick: () -> Unit,
    onEditProfile: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Avatar(user = user, size = 96.dp)
        Spacer(Modifier.height(12.dp))
        ProfileNames(user)
        Spacer(Modifier.height(16.dp))
        ProfileStats(
            following = user.followingCount,
            followers = rememberFollowerCount(user),
            likes = user.likeCount,
            onFollowingClick = onFollowingClick,
            onFollowersClick = onFollowersClick,
        )
        Spacer(Modifier.height(16.dp))
        if (isOwn) {
            OwnProfileActions(onEditProfile = onEditProfile, onShare = onShare)
        } else {
            OtherProfileActions(user = user, onShare = onShare)
        }
        ProfileBio(bio = user.bio, isOwn = isOwn, onEditProfile = onEditProfile)
    }
}

/** The loaded follower count, adjusted while the viewer's follow state differs from the one it was loaded with. */
@Composable
private fun rememberFollowerCount(user: UserDto): Long {
    val following = rememberIsFollowing(user)
    val delta = when {
        following == user.isFollowing -> 0L
        following -> 1L
        else -> -1L
    }
    return (user.followerCount + delta).coerceAtLeast(0L)
}

@Composable
private fun ProfileNames(user: UserDto) {
    Row(
        modifier = Modifier.padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = user.displayName,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (user.verified) {
            VerifiedBadge(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(18.dp),
            )
        }
    }
    Text(
        text = "@${user.username}",
        style = MaterialTheme.typography.bodyMedium,
        color = LoopColors.TextSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp),
    )
}

@Composable
private fun ProfileStats(
    following: Long,
    followers: Long,
    likes: Long,
    onFollowingClick: () -> Unit,
    onFollowersClick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatItem(count = following, label = "Following", onClick = onFollowingClick)
        StatSeparator()
        StatItem(count = followers, label = "Followers", onClick = onFollowersClick)
        StatSeparator()
        StatItem(count = likes, label = "Likes", onClick = null)
    }
}

@Composable
private fun StatItem(count: Long, label: String, onClick: (() -> Unit)?) {
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Column(
        modifier = Modifier
            .widthIn(min = 88.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(clickModifier)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = formatCount(count),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = LoopColors.TextSecondary,
        )
    }
}

@Composable
private fun StatSeparator() {
    VerticalDivider(modifier = Modifier.height(14.dp), thickness = 1.dp, color = LoopColors.Divider)
}

@Composable
private fun OwnProfileActions(onEditProfile: () -> Unit, onShare: () -> Unit) {
    Row(
        modifier = Modifier
            .widthIn(max = 360.dp)
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ProfileActionButton(label = "Edit profile", onClick = onEditProfile, modifier = Modifier.weight(1f))
        ProfileActionButton(label = "Share profile", onClick = onShare, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ProfileActionButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
private fun OtherProfileActions(user: UserDto, onShare: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FollowButton(user = user, modifier = Modifier.width(168.dp))
        FilledTonalIconButton(
            onClick = onShare,
            modifier = Modifier.size(38.dp),
            shape = RoundedCornerShape(8.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Icon(
                imageVector = Icons.Outlined.Share,
                contentDescription = "Share profile",
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ProfileBio(bio: String, isOwn: Boolean, onEditProfile: () -> Unit) {
    val modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 16.dp)
    when {
        bio.isNotBlank() -> CaptionText(
            text = bio,
            modifier = modifier,
            style = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 6,
        )
        isOwn -> Text(
            text = "Tap Edit profile to add a bio",
            modifier = modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClick = onEditProfile),
            style = MaterialTheme.typography.bodyMedium,
            color = LoopColors.TextMuted,
        )
    }
}
