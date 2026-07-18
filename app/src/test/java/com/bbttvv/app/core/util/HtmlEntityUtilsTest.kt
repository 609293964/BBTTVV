package com.bbttvv.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlEntityUtilsTest {
    @Test
    fun unescapeDecodesSingleQuoteEntityFromBilibiliComments() {
        assertEquals("I'm here", HtmlEntityUtils.unescape("I&#39;m here"))
    }

    @Test
    fun unescapeDecodesSingleQuoteEntityWithoutTrailingSemicolon() {
        assertEquals("I'm", HtmlEntityUtils.unescape("I&#39m"))
    }

    @Test
    fun unescapeDecodesCommonNamedEntities() {
        assertEquals(
            "a & b < c > d \"e\" 'f'",
            HtmlEntityUtils.unescape("a &amp; b &lt; c &gt; d &quot;e&quot; &apos;f&apos;")
        )
    }

    @Test
    fun unescapeDecodesDecimalAndHexNumericEntities() {
        assertEquals("'$", HtmlEntityUtils.unescape("&#39;&#36;"))
        assertEquals("star=*", HtmlEntityUtils.unescape("star=&#42;"))
        assertEquals("\u2605", HtmlEntityUtils.unescape("&#x2605;"))
    }

    @Test
    fun unescapeDecodesSupplementaryCodePointsAsSurrogatePairs() {
        assertEquals(String(Character.toChars(0x1F600)), HtmlEntityUtils.unescape("&#x1F600;"))
    }

    @Test
    fun unescapeLeavesPlainTextAndUnknownEntitiesUntouched() {
        assertEquals("hello [doge] world", HtmlEntityUtils.unescape("hello [doge] world"))
        assertEquals("AT&T", HtmlEntityUtils.unescape("AT&T"))
        assertEquals("a &unknown; b", HtmlEntityUtils.unescape("a &unknown; b"))
    }
}
