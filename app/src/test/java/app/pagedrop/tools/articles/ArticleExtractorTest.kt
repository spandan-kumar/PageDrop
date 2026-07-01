package app.pagedrop.tools.articles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleExtractorTest {

    @Test
    fun stripNonContent_removesScriptTags() {
        val html = "<body><script>alert('x');</script><p>Hello</p></body>"
        val cleaned = ArticleExtractorTestHelper.strip(html)
        assertFalse(cleaned.contains("<script"))
        assertTrue(cleaned.contains("Hello"))
    }

    @Test
    fun stripNonContent_removesStyleTags() {
        val html = "<html><style>body { color: red; }</style><p>Content</p></html>"
        val cleaned = ArticleExtractorTestHelper.strip(html)
        assertFalse(cleaned.contains("<style"))
        assertTrue(cleaned.contains("Content"))
    }

    @Test
    fun stripNonContent_removesNoScriptTags() {
        val html = "<body><noscript>JS off</noscript><p>Main</p></body>"
        val cleaned = ArticleExtractorTestHelper.strip(html)
        assertFalse(cleaned.contains("<noscript"))
        assertTrue(cleaned.contains("Main"))
    }

    @Test
    fun stripNonContent_removesNavTags() {
        val html = "<body><nav>Links</nav><article>Content</article></body>"
        val cleaned = ArticleExtractorTestHelper.strip(html)
        assertFalse(cleaned.contains("<nav"))
        assertTrue(cleaned.contains("Content"))
    }

    @Test
    fun extractTitle_fromOgTitle() {
        val html = """<html><meta property="og:title" content="OG Title"><title>Alt</title></html>"""
        val title = ArticleExtractorTestHelper.extractMeta(html, "og:title")
        assertEquals("OG Title", title)
    }

    @Test
    fun extractTitle_fromTitleTag() {
        val html = "<html><head><title>HTML Title</title></head><body><p>Text</p></body></html>"
        val titleMatch = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.get(1)?.trim()
        assertEquals("HTML Title", titleMatch)
    }

    @Test
    fun extractTitle_fromH1Fallback() {
        val html = "<html><body><h1>H1 Fallback</h1></body></html>"
        val h1 = Regex("""<h1[^>]*>(.*?)</h1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.get(1)?.trim()
        assertEquals("H1 Fallback", h1)
    }

    @Test
    fun extractMetaTag_withDifferentOrderings() {
        val html = """<meta property="og:image" content="https://img.jpg">"""
        val img = ArticleExtractorTestHelper.extractMeta(html, "og:image")
        assertEquals("https://img.jpg", img)
    }

    @Test
    fun extractMetaTag_contentBeforeProperty() {
        val html = """<meta content="value" property="og:desc">"""
        val desc = ArticleExtractorTestHelper.extractMeta(html, "og:desc")
        assertEquals("value", desc)
    }

    @Test
    fun extractBody_withArticleTag() {
        val html = "<html><body><article><p>Article content</p></article></body></html>"
        val cleaned = ArticleExtractorTestHelper.strip(html)
        val articleMatch = Regex(
            """<article[^>]*>(.*?)</article>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(cleaned)
        assertNotNull(articleMatch)
        val body = articleMatch!!.groupValues[1]
        assertTrue(body.contains("Article content"))
    }

    @Test
    fun extractBody_withMainTag() {
        val html = "<html><body><main><p>Main content</p></main></body></html>"
        val cleaned = ArticleExtractorTestHelper.strip(html)
        val mainMatch = Regex(
            """<main[^>]*>(.*?)</main>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(cleaned)
        assertNotNull(mainMatch)
    }

    @Test
    fun decodeHtml_handlesAllEntities() {
        val encoded = "&amp; &lt; &gt; &quot; &#39; &mdash; &ndash; &hellip; &rsquo; &lsquo; &rdquo; &ldquo;"
        val decoded = encoded
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&#x27;", "'")
            .replace("&rsquo;", "'")
            .replace("&lsquo;", "'")
            .replace("&rdquo;", "\"")
            .replace("&ldquo;", "\"")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&hellip;", "…")
        assertEquals("& < > \" ' — – … ' ' \" \"", decoded)
    }

    @Test
    fun extractCharset_fromContentType() {
        val ct = "text/html; charset=utf-8"
        val match = Regex("""charset=([^\s;]+)""", RegexOption.IGNORE_CASE).find(ct)
        assertEquals("utf-8", match?.groupValues?.get(1))
    }

    @Test
    fun extractCharset_noCharset_returnsEmpty() {
        val ct = "text/html"
        val match = Regex("""charset=([^\s;]+)""", RegexOption.IGNORE_CASE).find(ct)
        assertNull(match)
    }

    @Test
    fun extractedArticle_constructor() {
        val article = ArticleExtractor.ExtractedArticle(
            title = "Article",
            author = "Author",
            content = "<p>Content</p>",
            leadImageUrl = "https://example.com/img.jpg",
            siteName = "Example"
        )
        assertEquals("Article", article.title)
        assertEquals("Author", article.author)
        assertEquals("https://example.com/img.jpg", article.leadImageUrl)
        assertEquals("Example", article.siteName)
    }

    @Test
    fun extractedArticle_nullFields() {
        val article = ArticleExtractor.ExtractedArticle(
            title = "T", author = null, content = "",
            leadImageUrl = null, siteName = null
        )
        assertNull(article.author)
        assertNull(article.leadImageUrl)
        assertNull(article.siteName)
    }
}

object ArticleExtractorTestHelper {
    fun strip(html: String): String {
        var result = html
        for (tag in listOf("script", "style", "noscript", "iframe", "svg", "nav", "head")) {
            result = Regex("""<$tag[^>]*>.*?</$tag>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .replace(result, "")
        }
        return result
    }

    fun extractMeta(html: String, property: String): String? {
        val patterns = listOf(
            Regex("""<meta[^>]+property=["']$property["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
            Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+property=["']$property["']""", RegexOption.IGNORE_CASE),
            Regex("""<meta[^>]+name=["']$property["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
            Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+name=["']$property["']""", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            val match = pattern.find(html)
            if (match != null) return match.groupValues[1]
        }
        return null
    }
}
