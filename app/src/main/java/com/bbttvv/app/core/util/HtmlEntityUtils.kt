package com.bbttvv.app.core.util

/**
 * Lightweight HTML entity unescape helper with no Android dependencies.
 *
 * Bilibili comment APIs may return encoded text such as `&#39;`, `&quot;`, or
 * `&amp;`. Unknown entities are preserved so it is safe to call for arbitrary
 * user text.
 */
object HtmlEntityUtils {
    private val namedEntities = mapOf(
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        "nbsp" to " "
    )

    private val entityRegex = Regex("&(#[0-9]+|#[xX][0-9a-fA-F]+|[a-zA-Z][a-zA-Z0-9]*);?")

    fun unescape(input: String): String {
        if (input.isEmpty() || '&' !in input) return input
        return entityRegex.replace(input) { match ->
            decodeToken(match.groupValues[1]) ?: match.value
        }
    }

    private fun decodeToken(token: String): String? {
        namedEntities[token]?.let { return it }
        return when {
            token.startsWith("#x") || token.startsWith("#X") -> token
                .drop(2)
                .toIntOrNull(16)
                ?.takeIf { it in 1..0x10FFFF }
                ?.let(::codePointToString)
            token.startsWith("#") -> token
                .drop(1)
                .toIntOrNull()
                ?.takeIf { it in 1..0x10FFFF }
                ?.let(::codePointToString)
            else -> null
        }
    }

    private fun codePointToString(codePoint: Int): String {
        return if (codePoint <= Char.MAX_VALUE.code) {
            Char(codePoint).toString()
        } else {
            String(Character.toChars(codePoint))
        }
    }
}
