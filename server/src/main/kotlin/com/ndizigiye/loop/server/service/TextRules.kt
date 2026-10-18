package com.ndizigiye.loop.server.service

import com.ndizigiye.loop.server.ApiException

/**
 * Text that every database backend can store. PostgreSQL rejects U+0000 in text values (also
 * as a query parameter), so no backend accepts it: input with it is invalid, and a lookup by
 * such a name finds nothing.
 */
object TextRules {
    fun canStore(text: String): Boolean = '\u0000' !in text

    /** [text], or a validation error if it contains U+0000. */
    fun requireStorable(text: String): String {
        if (!canStore(text)) throw ApiException.validation("Text can't contain NUL characters.")
        return text
    }
}
