package app.pagedrop.tools.articles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleMobiConverterTest {

    @Test
    fun buildArticleHtml_includesTitle() {
        val article = ArticleExtractor.ExtractedArticle(
            title = "Test Article",
            author = "Test Author",
            content = "<p>Body text</p>",
            leadImageUrl = null,
            siteName = "TestSite"
        )
        val html = ArticleMobiConverterTestHelper.buildHtml(article)
        assertTrue(html.contains("<h1>Test Article</h1>"))
        assertTrue(html.contains("Test Author"))
    }

    @Test
    fun buildArticleHtml_includesContent() {
        val article = ArticleExtractor.ExtractedArticle(
            title = "T", author = "A",
            content = "<p>Content</p>",
            leadImageUrl = null, siteName = null
        )
        val html = ArticleMobiConverterTestHelper.buildHtml(article)
        assertTrue(html.contains("<p>Content</p>"))
    }

    @Test
    fun buildArticleHtml_withLeadImage() {
        val article = ArticleExtractor.ExtractedArticle(
            title = "T", author = "A",
            content = "<p>Body</p>",
            leadImageUrl = "https://example.com/image.jpg",
            siteName = null
        )
        val html = ArticleMobiConverterTestHelper.buildHtml(article)
        assertTrue(html.contains("<img"))
        assertTrue(html.contains("example.com/image.jpg"))
    }

    @Test
    fun buildArticleHtml_withSiteName() {
        val article = ArticleExtractor.ExtractedArticle(
            title = "T", author = null,
            content = "<p>Body</p>",
            leadImageUrl = null, siteName = "Example"
        )
        val html = ArticleMobiConverterTestHelper.buildHtml(article)
        assertTrue(html.contains("Example"))
    }

    @Test
    fun buildArticleHtml_htmlEntitiesEscaped() {
        val article = ArticleExtractor.ExtractedArticle(
            title = "T & A <Test>", author = null,
            content = "", leadImageUrl = null, siteName = null
        )
        val html = ArticleMobiConverterTestHelper.buildHtml(article)
        assertTrue(html.contains("&amp;"))
        assertTrue(html.contains("&lt;"))
    }

    @Test
    fun buildArticleHtml_structure() {
        val article = ArticleExtractor.ExtractedArticle(
            title = "T", author = null,
            content = "<p>Body</p>",
            leadImageUrl = null, siteName = null
        )
        val html = ArticleMobiConverterTestHelper.buildHtml(article)
        assertTrue(html.startsWith("<html>"))
        assertTrue(html.endsWith("</html>"))
        assertTrue(html.contains("<hr/>"))
        assertTrue(html.contains("</body>"))
    }

    @Test
    fun extractedArticle_equality() {
        val a = ArticleExtractor.ExtractedArticle("T", "A", "<p>C</p>", null, null)
        val b = ArticleExtractor.ExtractedArticle("T", "A", "<p>C</p>", null, null)
        assertTrue(a.title == b.title && a.author == b.author && a.content == b.content)
    }
}

object ArticleMobiConverterTestHelper {
    fun buildHtml(article: ArticleExtractor.ExtractedArticle): String {
        val sb = StringBuilder()
        sb.append("<html><head><title>")
        sb.append(escapeHtml(article.title))
        sb.append("</title></head><body>\n")
        sb.append("<h1>").append(escapeHtml(article.title)).append("</h1>\n")
        if (!article.author.isNullOrBlank()) {
            sb.append("<p><em>By ").append(escapeHtml(article.author)).append("</em></p>\n")
        }
        if (article.leadImageUrl != null) {
            sb.append("<img src=\"").append(article.leadImageUrl).append("\"/>\n")
        }
        sb.append("<hr/>\n").append(article.content).append("\n<hr/>\n")
        article.siteName?.let { name ->
            sb.append("<p><small>Source: ").append(escapeHtml(name)).append("</small></p>\n")
        }
        sb.append("</body></html>")
        return sb.toString()
    }

    private fun escapeHtml(text: String): String = text
        .replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;")
}
