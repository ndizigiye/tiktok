package com.ndizigiye.loop.ui.common

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import com.ndizigiye.loop.common.CaptionParser
import com.ndizigiye.loop.ui.navigation.LocalNavigator

private val TokenLinkStyles = TextLinkStyles(style = SpanStyle(fontWeight = FontWeight.Bold))

/**
 * Caption or comment text with tappable, bold #hashtags and @mentions.
 * By default a hashtag opens its page and a mention opens the profile; the
 * callbacks receive the lowercase name without '#' / '@'.
 */
@Composable
fun CaptionText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    onHashtag: (String) -> Unit = LocalNavigator.current::openHashtag,
    onMention: (String) -> Unit = LocalNavigator.current::openProfile,
) {
    val currentOnHashtag by rememberUpdatedState(onHashtag)
    val currentOnMention by rememberUpdatedState(onMention)
    val annotated: AnnotatedString = remember(text) {
        buildAnnotatedString {
            for (token in CaptionParser.tokenize(text)) {
                when (token) {
                    is CaptionParser.Token.Plain -> append(token.text)
                    is CaptionParser.Token.Hashtag -> {
                        val link = LinkAnnotation.Clickable(
                            tag = "hashtag:${token.name}",
                            styles = TokenLinkStyles,
                            linkInteractionListener = LinkInteractionListener { currentOnHashtag(token.name) },
                        )
                        withLink(link) { append(token.text) }
                    }
                    is CaptionParser.Token.Mention -> {
                        val link = LinkAnnotation.Clickable(
                            tag = "mention:${token.username}",
                            styles = TokenLinkStyles,
                            linkInteractionListener = LinkInteractionListener { currentOnMention(token.username) },
                        )
                        withLink(link) { append(token.text) }
                    }
                }
            }
        }
    }
    Text(
        text = annotated,
        modifier = modifier,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
