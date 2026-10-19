package com.ndizigiye.loop.ui.feed

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.ndizigiye.loop.api.SoundDto
import com.ndizigiye.loop.api.VideoDto
import com.ndizigiye.loop.common.CaptionParser
import com.ndizigiye.loop.ui.common.CaptionText
import com.ndizigiye.loop.ui.navigation.LocalNavigator
import com.ndizigiye.loop.ui.theme.LoopColors

private const val COLLAPSED_LINES = 2
private val ExpandedCaptionMaxHeight = 240.dp

/**
 * The author's handle, the caption (two lines with a "more"/"less" toggle when it
 * is longer) and the sound, at the bottom-start of a feed page. The sound title
 * scrolls only on the [isCurrent] page.
 */
@Composable
internal fun VideoInfo(
    video: VideoDto,
    isCurrent: Boolean,
    modifier: Modifier = Modifier,
) {
    val navigator = LocalNavigator.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "@${video.author.username}",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                shadow = OverlayTextShadow,
            ),
            color = LoopColors.OverlayText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable(onClickLabel = "Open profile", role = Role.Button) {
                navigator.openProfile(video.author.username)
            },
        )
        if (video.caption.isNotBlank()) {
            ExpandableCaption(caption = video.caption, videoId = video.id)
        }
        SoundRow(
            sound = video.sound,
            animate = isCurrent,
            onClick = { navigator.openSound(video.sound.id) },
        )
    }
}

@Composable
private fun ExpandableCaption(caption: String, videoId: Long) {
    var expanded by rememberSaveable(videoId) { mutableStateOf(false) }
    val style = MaterialTheme.typography.bodyMedium.copy(shadow = OverlayTextShadow)
    val measurer = rememberTextMeasurer()
    val scrollState = rememberScrollState()

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val maxWidthPx = constraints.maxWidth
        // CaptionText exposes no onTextLayout, so the collapsed layout is measured
        // here with the same style (and bold tokens) to learn whether it overflows.
        val overflows = remember(caption, style, maxWidthPx) {
            maxWidthPx != Constraints.Infinity &&
                measurer.measure(
                    text = boldTokens(caption),
                    style = style,
                    maxLines = COLLAPSED_LINES,
                    constraints = Constraints(maxWidth = maxWidthPx),
                ).hasVisualOverflow
        }
        Column(
            modifier = if (expanded) {
                Modifier
                    .heightIn(max = ExpandedCaptionMaxHeight)
                    .verticalScroll(scrollState)
            } else {
                Modifier
            },
        ) {
            CaptionText(
                text = caption,
                style = style,
                color = LoopColors.OverlayTextSecondary,
                maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_LINES,
            )
            if (overflows) {
                Text(
                    text = if (expanded) "less" else "more",
                    style = style.copy(fontWeight = FontWeight.Bold),
                    color = LoopColors.OverlayText,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clickable(role = Role.Button) { expanded = !expanded },
                )
            }
        }
    }
}

/** The caption as CaptionText lays it out: hashtags and mentions in bold. */
private fun boldTokens(caption: String): AnnotatedString = buildAnnotatedString {
    for (token in CaptionParser.tokenize(caption)) {
        when (token) {
            is CaptionParser.Token.Plain -> append(token.text)
            is CaptionParser.Token.Hashtag,
            is CaptionParser.Token.Mention,
            -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(token.text) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SoundRow(sound: SoundDto, animate: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .clickable(onClickLabel = "Open sound", role = Role.Button, onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShadowedIcon(
            imageVector = Icons.Filled.MusicNote,
            contentDescription = null,
            tint = LoopColors.OverlayText,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "${sound.title} · ${sound.authorName}",
            style = MaterialTheme.typography.bodyMedium.copy(shadow = OverlayTextShadow),
            color = LoopColors.OverlayText,
            maxLines = 1,
            overflow = if (animate) TextOverflow.Clip else TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .then(if (animate) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier),
        )
    }
}
