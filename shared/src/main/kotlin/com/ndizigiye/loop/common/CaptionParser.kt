package com.ndizigiye.loop.common

/**
 * Splits a caption into plain text, #hashtag and @mention tokens.
 *
 * A hashtag is '#' followed by letters, digits or underscores; a mention is '@'
 * followed by a username (letters, digits, '.', '_'). A marker only starts a
 * token at the beginning of the text or after a non-word character, so
 * "mail@example.com" and "a#b" stay plain text. Hashtag names are compared
 * case-insensitively and normalised to lowercase.
 */
object CaptionParser {
    sealed interface Token {
        val text: String

        data class Plain(override val text: String) : Token

        /** [text] includes the leading '#'; [name] is lowercase without it. */
        data class Hashtag(override val text: String, val name: String) : Token

        /** [text] includes the leading '@'; [username] is lowercase without it. */
        data class Mention(override val text: String, val username: String) : Token
    }

    fun tokenize(caption: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val plain = StringBuilder()
        var i = 0
        while (i < caption.length) {
            val c = caption[i]
            val atBoundary = i == 0 || !isWordChar(caption[i - 1])
            if ((c == '#' || c == '@') && atBoundary) {
                var end = i + 1
                val allowed: (Char) -> Boolean = if (c == '#') ::isHashtagChar else ::isUsernameChar
                while (end < caption.length && allowed(caption[end])) end++
                // A mention never ends in '.', so "@bob." mentions "bob".
                if (c == '@') while (end > i + 1 && caption[end - 1] == '.') end--
                if (end > i + 1) {
                    if (plain.isNotEmpty()) {
                        tokens += Token.Plain(plain.toString())
                        plain.clear()
                    }
                    val text = caption.substring(i, end)
                    val name = text.substring(1).lowercase()
                    tokens += if (c == '#') Token.Hashtag(text, name) else Token.Mention(text, name)
                    i = end
                    continue
                }
            }
            plain.append(c)
            i++
        }
        if (plain.isNotEmpty()) tokens += Token.Plain(plain.toString())
        return tokens
    }

    /** Distinct lowercase hashtag names in order of first appearance. */
    fun hashtags(caption: String): List<String> =
        tokenize(caption).filterIsInstance<Token.Hashtag>().map { it.name }.distinct()

    /** Distinct lowercase mentioned usernames in order of first appearance. */
    fun mentions(caption: String): List<String> =
        tokenize(caption).filterIsInstance<Token.Mention>().map { it.username }.distinct()

    private fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '_'

    private fun isHashtagChar(c: Char) = c.isLetterOrDigit() || c == '_'

    private fun isUsernameChar(c: Char) = c.isLetterOrDigit() || c == '_' || c == '.'
}
