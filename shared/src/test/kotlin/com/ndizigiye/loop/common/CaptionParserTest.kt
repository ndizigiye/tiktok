package com.ndizigiye.loop.common

import com.ndizigiye.loop.common.CaptionParser.Token
import kotlin.test.Test
import kotlin.test.assertEquals

class CaptionParserTest {
    @Test
    fun `splits hashtags mentions and plain text`() {
        val tokens = CaptionParser.tokenize("Sunset run with @Ana.B #Running #fyp!")
        assertEquals(
            listOf(
                Token.Plain("Sunset run with "),
                Token.Mention("@Ana.B", "ana.b"),
                Token.Plain(" "),
                Token.Hashtag("#Running", "running"),
                Token.Plain(" "),
                Token.Hashtag("#fyp", "fyp"),
                Token.Plain("!"),
            ),
            tokens,
        )
    }

    @Test
    fun `markers inside words are plain text`() {
        assertEquals(listOf(Token.Plain("mail me at bob@example.com or a#b")),
            CaptionParser.tokenize("mail me at bob@example.com or a#b"))
    }

    @Test
    fun `lone markers are plain text`() {
        assertEquals(listOf(Token.Plain("# @ ok")), CaptionParser.tokenize("# @ ok"))
    }

    @Test
    fun `mention drops trailing dots`() {
        assertEquals(listOf("bob"), CaptionParser.mentions("thanks @bob."))
    }

    @Test
    fun `hashtags are distinct lowercase in order`() {
        assertEquals(listOf("dance", "fyp"), CaptionParser.hashtags("#Dance #fyp #DANCE"))
    }

    @Test
    fun `tokens round trip to the original text`() {
        val caption = "a #b @c.d. e#f #g_1"
        assertEquals(caption, CaptionParser.tokenize(caption).joinToString("") { it.text })
    }
}
