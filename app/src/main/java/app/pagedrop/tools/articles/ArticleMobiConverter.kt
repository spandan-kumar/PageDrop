package app.pagedrop.tools.articles

import android.util.Log
import app.pagedrop.converter.MobiWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ArticleMobiConverter {
    private const val TAG = "ArticleMobiConvert"

    suspend fun convertArticleToMobi(
        article: ArticleExtractor.ExtractedArticle,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val html = buildArticleHtml(article)
            val writer = MobiWriter(
                title = article.title,
                author = article.author ?: article.siteName ?: "Unknown Author",
                htmlContent = html.toByteArray(Charsets.UTF_8)
            )
            writer.write(outputFile)
            Log.d(TAG, "Article MOBI written: ${outputFile.length()} bytes")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Article MOBI conversion failed: ${e.message}", e)
            if (outputFile.exists()) outputFile.delete()
            false
        }
    }

    private fun buildArticleHtml(article: ArticleExtractor.ExtractedArticle): String {
        val sb = StringBuilder()
        sb.append("<html><head><title>")
        sb.append(escapeHtml(article.title))
        sb.append("</title></head><body>\n")

        sb.append("<h1>")
        sb.append(escapeHtml(article.title))
        sb.append("</h1>\n")

        if (!article.author.isNullOrBlank()) {
            sb.append("<p><em>By ")
            sb.append(escapeHtml(article.author))
            sb.append("</em></p>\n")
        }

        if (article.leadImageUrl != null) {
            sb.append("<img src=\"")
            sb.append(article.leadImageUrl)
            sb.append("\"/>\n")
        }

        sb.append("<hr/>\n")
        sb.append(article.content)
        sb.append("\n<hr/>\n")

        article.siteName?.let { site ->
            sb.append("<p><small>Source: ")
            sb.append(escapeHtml(site))
            sb.append("</small></p>\n")
        }

        sb.append("</body></html>")
        return sb.toString()
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}
