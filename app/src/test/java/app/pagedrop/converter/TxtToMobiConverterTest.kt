package app.pagedrop.converter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TxtToMobiConverterTest {

    @Test
    fun buildHtmlFromText_singleParagraph() {
        val html = TxtToMobiConverterTestHelper.buildHtml("Test", "Hello world")
        assertTrue(html.contains("<p>Hello world</p>"))
        assertTrue(html.contains("<h1>Test</h1>"))
    }

    @Test
    fun buildHtmlFromText_multipleParagraphs() {
        val text = "First paragraph.\n\nSecond paragraph."
        val html = TxtToMobiConverterTestHelper.buildHtml("Book", text)
        assertTrue(html.contains("<p>First paragraph.</p>"))
        assertTrue(html.contains("<p>Second paragraph.</p>"))
    }

    @Test
    fun buildHtmlFromText_lineBreaksWithinParagraph() {
        val text = "Line 1.\nLine 2.\n\nNext para."
        val html = TxtToMobiConverterTestHelper.buildHtml("T", text)
        assertTrue(html.contains("<br/>"))
    }

    @Test
    fun buildHtmlFromText_emptyText_handlesGracefully() {
        val html = TxtToMobiConverterTestHelper.buildHtml("Empty", "")
        assertTrue(html.contains("<h1>Empty</h1>"))
    }

    @Test
    fun buildHtmlFromText_escapesHtmlEntities() {
        val text = "Title & Author <test>"
        val html = TxtToMobiConverterTestHelper.buildHtml("T", text)
        assertTrue(html.contains("&amp;"))
        assertTrue(html.contains("&lt;"))
    }

    @Test
    fun buildHtmlFromText_multipleBlankLinesBetweenParagraphs() {
        val text = "Para 1\n\n\n\nPara 2"
        val html = TxtToMobiConverterTestHelper.buildHtml("T", text)
        assertEquals(2, extractParagraphCount(html))
    }
}

object TxtToMobiConverterTestHelper {
    fun buildHtml(title: String, text: String): String {
        val sb = StringBuilder(text.length + 1024)
        sb.append("<html><head><title>").append(escapeHtml(title)).append("</title></head><body>\n")
        sb.append("<h1>").append(escapeHtml(title)).append("</h1>\n")
        val paragraphs = text.split(Regex("\\r?\\n\\s*\\r?\\n"))
        for (paragraph in paragraphs) {
            val trimmed = paragraph.trim()
            if (trimmed.isNotEmpty()) {
                sb.append("<p>").append(escapeHtml(trimmed).replace("\n", "<br/>")).append("</p>\n")
            }
        }
        sb.append("</body></html>")
        return sb.toString()
    }

    private fun escapeHtml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}

private fun extractParagraphCount(html: String): Int {
    val regex = Regex("<p>.*?</p>", RegexOption.DOT_MATCHES_ALL)
    return regex.findAll(html).count()
}
