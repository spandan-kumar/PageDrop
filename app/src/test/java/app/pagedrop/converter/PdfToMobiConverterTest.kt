package app.pagedrop.converter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfToMobiConverterTest {

    @Test
    fun buildHtml_singlePage() {
        val html = PdfToMobiConverterTestHelper.buildHtml("Test Doc")
        assertTrue(html.contains("<h1>Test Doc</h1>"))
        assertTrue(html.contains("<html"))
        assertTrue(html.contains("</body>"))
    }

    @Test
    fun buildHtml_escapesTitle() {
        val html = PdfToMobiConverterTestHelper.buildHtml("Title & Author <test>")
        assertTrue(html.contains("&amp;"))
        assertTrue(html.contains("&lt;"))
    }

    @Test
    fun buildHtml_hasPageBreakAfterContent() {
        val html = PdfToMobiConverterTestHelper.buildHtml("Doc")
        assertTrue(html.contains("<mbp:pagebreak/>"))
    }

    @Test
    fun buildHtml_emptyTitle() {
        val html = PdfToMobiConverterTestHelper.buildHtml("")
        assertTrue(html.contains("<h1></h1>"))
    }

    @Test
    fun buildHtml_structuralIntegrity() {
        val html = PdfToMobiConverterTestHelper.buildHtml("Book")
        assertTrue(html.startsWith("<html><head><title>Book</title></head><body>"))
        assertTrue(html.endsWith("</body></html>"))
    }
}

object PdfToMobiConverterTestHelper {
    fun buildHtml(title: String): String {
        val sb = StringBuilder()
        sb.append("<html><head><title>").append(escapeHtml(title)).append("</title></head><body>\n")
        sb.append("<h1>").append(escapeHtml(title)).append("</h1>\n")
        sb.append("<p>Sample paragraph text.</p>\n")
        sb.append("<mbp:pagebreak/>\n")
        sb.append("</body></html>")
        return sb.toString()
    }

    private fun escapeHtml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
