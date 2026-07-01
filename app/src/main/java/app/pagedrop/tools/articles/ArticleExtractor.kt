package app.pagedrop.tools.articles

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object ArticleExtractor {
    private const val TAG = "ArticleExtractor"
    private const val MAX_CONTENT_LENGTH = 2_000_000 // 2MB limit

    data class ExtractedArticle(
        val title: String,
        val author: String?,
        val content: String,
        val leadImageUrl: String?,
        val siteName: String?
    )

    suspend fun extract(url: String): Result<ExtractedArticle> = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (compatible; PageDrop/1.0)"
            )
            connection.instanceFollowRedirects = true

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                return@withContext Result.failure(
                    IllegalStateException("HTTP $responseCode")
                )
            }

            val contentType = connection.contentType ?: ""
            val charset = extractCharset(contentType).ifBlank { "UTF-8" }

            val html = connection.inputStream.use { stream ->
                BufferedReader(InputStreamReader(stream, charset)).readText()
            }

            if (html.length > MAX_CONTENT_LENGTH) {
                return@withContext Result.failure(
                    IllegalStateException("Content too large (${html.length} bytes)")
                )
            }

            val article = extractContent(html, url)
            Result.success(article)
        } catch (e: Exception) {
            Log.e(TAG, "Article extraction failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun extractContent(html: String, sourceUrl: String): ExtractedArticle {
        val cleaned = stripNonContent(html)

        val title = extractTitle(cleaned)
        val author = extractAuthor(cleaned)
        val siteName = extractSiteName(sourceUrl, cleaned)
        val leadImage = extractLeadImage(cleaned)
        val body = extractBody(cleaned, sourceUrl)

        return ExtractedArticle(
            title = title,
            author = author,
            content = body,
            leadImageUrl = leadImage,
            siteName = siteName
        )
    }

    private fun stripNonContent(html: String): String {
        var result = html
        for (tag in listOf("script", "style", "noscript", "iframe", "svg", "nav", "head")) {
            result = Regex("""<$tag[^>]*>.*?</$tag>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .replace(result, "")
        }
        return result
    }

    private fun extractTitle(html: String): String {
        // Try og:title
        val ogTitle = extractMetaTag(html, "og:title")
        if (!ogTitle.isNullOrBlank()) return decodeHtml(ogTitle)

        // Try <title>
        val titleTag = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.get(1)?.trim()
        if (!titleTag.isNullOrBlank()) return decodeHtml(titleTag)

        // Try h1
        val h1 = Regex("""<h1[^>]*>(.*?)</h1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.get(1)?.trim()
        if (!h1.isNullOrBlank()) return stripTags(h1)

        return "Untitled Article"
    }

    private fun extractAuthor(html: String): String? {
        val ogAuthor = extractMetaTag(html, "article:author")
            ?: extractMetaTag(html, "author")
        return ogAuthor?.let { decodeHtml(it) }
    }

    private fun extractSiteName(sourceUrl: String, html: String): String? {
        val ogSite = extractMetaTag(html, "og:site_name")
        if (!ogSite.isNullOrBlank()) return ogSite

        return try {
            URL(sourceUrl).host.removePrefix("www.")
        } catch (_: Exception) { null }
    }

    private fun extractLeadImage(html: String): String? {
        val ogImage = extractMetaTag(html, "og:image")
        if (!ogImage.isNullOrBlank()) return ogImage

        return Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)
    }

    private fun extractBody(html: String, sourceUrl: String): String {
        // Try article element first
        val articleContent = Regex(
            """<article[^>]*>(.*?)</article>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(html)

        if (articleContent != null) {
            return cleanBodyHtml(articleContent.groupValues[1])
        }

        // Try main content area
        val mainContent = Regex(
            """<main[^>]*>(.*?)</main>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(html)

        if (mainContent != null) {
            return cleanBodyHtml(mainContent.groupValues[1])
        }

        // Try common content containers
        for (selector in listOf(
            """class=["'][^"']*content[^"']*["']""",
            """id=["'][^"']*content[^"']*["']""",
            """class=["'][^"']*post[^"']*["']""",
            """class=["'][^"']*article[^"']*["']""",
            """class=["'][^"']*entry[^"']*["']"""
        )) {
            val match = Regex("""<div[^>]*$selector[^>]*>(.*?)</div>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ).find(html)
            if (match != null) {
                return cleanBodyHtml(match.groupValues[1])
            }
        }

        // Fallback: use body content
        val bodyContent = Regex(
            """<body[^>]*>(.*?)</body>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(html)
        return if (bodyContent != null) {
            cleanBodyHtml(bodyContent.groupValues[1])
        } else {
            "<p>Could not extract article content.</p>" +
            "<p><a href=\"$sourceUrl\">Read original</a></p>"
        }
    }

    private fun cleanBodyHtml(html: String): String {
        var result = html

        // Remove common non-content elements
        for (tag in listOf("header", "footer", "aside", "form", "iframe")) {
            result = Regex("""<$tag[^>]*>.*?</$tag>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .replace(result, "")
        }

        // Remove elements with common non-content class patterns
        val nonContentClasses = listOf(
            "comment", "sidebar", "widget", "advertisement", "ad-", "social",
            "share", "related", "recommend", "newsletter", "banner", "popup",
            "cookie", "consent"
        )
        for (cls in nonContentClasses) {
            result = Regex(
                """<[^>]+class=["'][^"']*$cls[^"']*["'][^>]*>.*?</[^>]+>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ).replace(result, "")
        }

        // Strip inline scripts and styles
        result = result.replace(Regex("""\s*(on\\w+)\s*=\s*["'][^"']*["']""", RegexOption.IGNORE_CASE), "")
        result = result.replace(Regex("""\s*style\s*=\s*["'][^"']*["']""", RegexOption.IGNORE_CASE), "")

        // Remove empty paragraphs and divs
        result = Regex("""<(p|div|span)[^>]*>\s*</\1>""", RegexOption.IGNORE_CASE).replace(result, "")

        return result.trim()
    }

    private fun extractMetaTag(html: String, property: String): String? {
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

    private fun stripTags(html: String): String {
        return html.replace(Regex("<[^>]+>"), "").trim()
    }

    private fun decodeHtml(text: String): String {
        return text
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
            .trim()
    }

    private fun extractCharset(contentType: String): String {
        val match = Regex("""charset=([^\s;]+)""", RegexOption.IGNORE_CASE).find(contentType)
        return match?.groupValues?.get(1) ?: ""
    }
}
