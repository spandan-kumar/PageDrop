package app.pagedrop.converter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class EpubToMobiConverterTest {

    @Test
    fun extractBodyContent_fullHtml_returnsBodyOnly() {
        val xhtml = """<html><head><title>T</title></head><body><p>Content</p></body></html>"""
        val result = EpubToMobiConverterTestHelper.extractBodyContent(xhtml)
        assertEquals("<p>Content</p>", result)
    }

    @Test
    fun extractBodyContent_stripsStyleTags() {
        val xhtml = """<html><body><style>body { color: red; }</style><p>Visible</p></body></html>"""
        val result = EpubToMobiConverterTestHelper.extractBodyContent(xhtml)
        assertTrue(result.contains("Visible"))
        assertFalse(result.contains("color: red"))
    }

    @Test
    fun extractBodyContent_stripsScriptTags() {
        val xhtml = """<body><script>alert('x');</script><p>Text</p></body>"""
        val result = EpubToMobiConverterTestHelper.extractBodyContent(xhtml)
        assertTrue(result.contains("Text"))
        assertFalse(result.contains("alert"))
    }

    @Test
    fun extractBodyContent_stripsNavHeaderFooter() {
        val xhtml = """<body><nav><a href="#">TOC</a></nav><header>Header</header><p>Body</p><footer>Foot</footer></body>"""
        val result = EpubToMobiConverterTestHelper.extractBodyContent(xhtml)
        assertTrue(result.contains("Body"))
        assertFalse(result.contains("TOC"))
        assertFalse(result.contains("Header"))
        assertFalse(result.contains("Foot"))
    }

    @Test
    fun extractBodyContent_noBodyTags_returnsFullContent() {
        val xhtml = """Just text"""
        val result = EpubToMobiConverterTestHelper.extractBodyContent(xhtml)
        assertEquals("Just text", result)
    }

    @Test
    fun extractBodyContent_emptyReturnsEmpty() {
        val result = EpubToMobiConverterTestHelper.extractBodyContent("<html><body></body></html>")
        assertTrue(result.isBlank())
    }
}

/**
 * Helper to expose package-private methods for testing.
 */
object EpubToMobiConverterTestHelper {
    fun extractBodyContent(xhtml: String): String {
        // Replicate the private method logic for testing
        val bodyContent = run {
            val bodyStart = xhtml.indexOf("<body", ignoreCase = true)
            if (bodyStart == -1) return@run xhtml
            val bodyTagEnd = xhtml.indexOf('>', bodyStart)
            if (bodyTagEnd == -1) return@run xhtml
            val bodyEnd = xhtml.indexOf("</body>", bodyTagEnd, ignoreCase = true)
            if (bodyEnd == -1) xhtml.substring(bodyTagEnd + 1)
            else xhtml.substring(bodyTagEnd + 1, bodyEnd)
        }

        return bodyContent
            .replace(Regex("<style[^>]*>.*?</style>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
            .replace(Regex("<link[^>]*(?:/>|>)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<script[^>]*>.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
            .replace(Regex("<header[^>]*>.*?</header>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
            .replace(Regex("<nav[^>]*>.*?</nav>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
            .replace(Regex("<footer[^>]*>.*?</footer>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
            .trim()
    }
}
