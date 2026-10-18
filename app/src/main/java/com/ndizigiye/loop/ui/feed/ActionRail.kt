package com.ndizigiye.loop.ui.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ndizigiye.loop.api.UserDto
import com.ndizigiye.loop.api.VideoDto
import com.ndizigiye.loop.common.formatCount
import com.ndizigiye.loop.ui.common.Avatar
import com.ndizigiye.loop.ui.common.rememberIsFollowing
import com.ndizigiye.loop.ui.common.rememberVideoStats
import com.ndizigiye.loop.ui.navigation.LocalAppContainer
import com.ndizigiye.loop.ui.navigation.LocalNavigator
import com.ndizigiye.loop.ui.theme.LoopColors
import com.ndizigiye.loop.ui.theme.LoopViolet
import kotlinx.coroutines.delay

internal val ActionRailWidth = 64.dp
private val AvatarSize = 48.dp
private val BadgeSize = 22.dp
private val RailIconSize = 34.dp
private const val FOLLOWED_CHECK_MS = 900L

/**
 * The vertical action column on the right of a feed page: author (with a follow
 * badge), like, comments, bookmark, share and the sound disc. Counts and toggle
 * states come from the shared InteractionStore.
 */
@Composable
internal fun ActionRail(
    video: VideoDto,
    isPlaying: Boolean,
    actions: VideoActions,
    onOpenComments: () -> Unit,
    onOpenShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navigator = LocalNavigator.current
    val stats = rememberVideoStats(video)
    val likeBounce = rememberToggleBounce(stats.liked)
    val bookmarkBounce = rememberToggleBounce(stats.bookmarked)

    Column(
        modifier = modifier.width(ActionRailWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AuthorAvatar(
            author = video.author,
            actions = actions,
            onClick = { navigator.openProfile(video.author.username) },
        )
        RailButton(
            icon = Icons.Filled.Favorite,
            label = formatCount(stats.likeCount),
            contentDescription = if (stats.liked) "Unlike" else "Like",
            tint = if (stats.liked) LoopColors.Like else LoopColors.OverlayText,
            iconModifier = Modifier.graphicsLayer {
                scaleX = likeBounce.value
                scaleY = likeBounce.value
            },
            onClick = { actions.toggleLike(video) },
        )
        RailButton(
            icon = Icons.Filled.ChatBubble,
            label = formatCount(stats.commentCount),
            contentDescription = "Comments",
            onClick = onOpenComments,
        )
        RailButton(
            icon = Icons.Filled.Bookmark,
            label = formatCount(stats.bookmarkCount),
            contentDescription = if (stats.bookmarked) "Remove from saved" else "Save",
            tint = if (stats.bookmarked) LoopColors.Bookmark else LoopColors.OverlayText,
            iconModifier = Modifier.graphicsLayer {
                scaleX = bookmarkBounce.value
                scaleY = bookmarkBounce.value
            },
            onClick = { actions.toggleBookmark(video) },
        )
        RailButton(
            icon = Icons.Filled.Share,
            label = formatCount(stats.shareCount),
            contentDescription = "Share",
            onClick = onOpenShare,
        )
        SoundDisc(
            author = video.author,
            isPlaying = isPlaying,
            onClick = { navigator.openSound(video.sound.id) },
        )
    }
}

/** The author's avatar; a "+" badge below it follows them and turns into a check before it goes. */
@Composable
private fun AuthorAvatar(author: UserDto, actions: VideoActions, onClick: () -> Unit) {
    val session by LocalAppContainer.current.sessionStore.session.collectAsStateWithLifecycle()
    val isMe = author.isMe || session?.user?.id == author.id
    val following = rememberIsFollowing(author)
    var justFollowed by remember(author.id) { mutableStateOf(false) }
    LaunchedEffect(justFollowed) {
        if (justFollowed) {
            delay(FOLLOWED_CHECK_MS)
            justFollowed = false
        }
    }

    Box(
        modifier = Modifier.size(width = AvatarSize, height = AvatarSize + BadgeSize / 2),
        contentAlignment = Alignment.TopCenter,
    ) {
        Avatar(
            user = author,
            size = AvatarSize,
            borderColor = Color.White,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClickLabel = "Open profile", role = Role.Button, onClick = onClick),
        )
        AnimatedVisibility(
            visible = !isMe && (!following || justFollowed),
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
        ) {
            FollowBadge(
                checked = justFollowed,
                onClick = { if (actions.follow(author)) justFollowed = true },
            )
        }
    }
}

@Composable
private fun FollowBadge(checked: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(
        targetValue = if (checked) Color.White else LoopViolet,
        label = "followBadgeBackground",
    )
    Box(
        modifier = Modifier
            .size(BadgeSize)
            .clip(CircleShape)
            .background(background)
            .clickable(enabled = !checked, onClickLabel = "Follow", role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(targetState = checked, label = "followBadgeIcon") { done ->
            Icon(
                imageVector = if (done) Icons.Rounded.Check else Icons.Rounded.Add,
                contentDescription = if (done) "Following" else "Follow",
                tint = if (done) LoopViolet else Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun RailButton(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = LoopColors.OverlayText,
    iconModifier: Modifier = Modifier,
) {
    Column(
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(bounded = false, radius = 28.dp),
            onClickLabel = contentDescription,
            role = Role.Button,
            onClick = onClick,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ShadowedIcon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier
                .size(RailIconSize)
                .then(iconModifier),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                shadow = OverlayTextShadow,
            ),
            color = LoopColors.OverlayText,
            maxLines = 1,
        )
    }
}

/** A scale that springs back from a squeeze whenever [active] changes after the first composition. */
@Composable
private fun rememberToggleBounce(active: Boolean): Animatable<Float, AnimationVector1D> {
    val scale = remember { Animatable(1f) }
    var shown by remember { mutableStateOf(active) }
    LaunchedEffect(active) {
        if (active == shown) return@LaunchedEffect
        shown = active
        scale.snapTo(0.6f)
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioHighBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        )
    }
    return scale
}
