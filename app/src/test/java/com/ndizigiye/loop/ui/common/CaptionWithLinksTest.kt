package com.ndizigiye.loop.ui.common

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CaptionWithLinksTest {

    private val opened = mutableListOf<String>()

    private fun links(caption: String): Pair<AnnotatedString, List<AnnotatedString.Range<LinkAnnotation.Clickable>>> {
        val annotated = captionWithLinks(caption, onHashtag = { opened += "#$it" }, onMention = { opened += "@$it" })
        val links = annotated.getLinkAnnotations(0, annotated.length).map { range ->
            AnnotatedString.Range(range.item as LinkAnnotation.Clickable, range.start, range.end)
        }
        return annotated to links
    }

    @Test
    fun `hashtags and mentions become links over exactly their text`() {
        val caption = "Sunset run with @Ana.B. #Running #fyp!"

        val (annotated, links) = links(caption)

        assertEquals(caption, annotated.text)
        assertEquals(
            listOf("mention:ana.b" to "@Ana.B", "hashtag:running" to "#Running", "hashtag:fyp" to "#fyp"),
            links.map { it.item.tag to caption.substring(it.start, it.end) },
        )
        assertTrue(links.all { it.item.styles?.style?.fontWeight == FontWeight.Bold })
    }

    @Test
    fun `tapping a link opens the lowercase hashtag or username`() {
        val (_, links) = links("@Ana.B loves #Running")

        links.forEach { it.item.linkInteractionListener!!.onClick(it.item) }

        assertEquals(listOf("@ana.b", "#running"), opened)
    }

    @Test
    fun `text without tokens has no links`() {
        val caption = "mail bob@example.com about a#b or # @ alone"

        val (annotated, links) = links(caption)

        assertEquals(caption, annotated.text)
        assertTrue(links.isEmpty())
    }
}
