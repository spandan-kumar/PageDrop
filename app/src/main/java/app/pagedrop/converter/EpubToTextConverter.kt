package app.pagedrop.converter

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.siegmann.epublib.domain.Resource
import nl.siegmann.epublib.epub.EpubReader
import java.io.File
import java.io.FileInputStream

/**
 * Extracts readable text from an EPUB file and writes it as a plain .txt file.
 *
 * Pipeline:
 *   1. Parse EPUB with epublib
 *   2. Extract text from each spine item (strip all HTML tags)
 *   3. Write concatenated text to output file
 */
object EpubToTextConverter {

    private const val TAG = "EpubToTextConverter"

    suspend fun convert(epubFile: File, txtFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting EPUB→TXT: ${epubFile.name} → ${txtFile.name}")

            val epub = FileInputStream(epubFile).use { EpubReader().readEpub(it) }

            val metadata = epub.metadata
            val title = metadata.firstTitle?.takeIf { it.isNotBlank() }
                ?: txtFile.nameWithoutExtension
            val author = metadata.authors.firstOrNull()?.let { a ->
                listOfNotNull(
                    a.firstname?.takeIf { it.isNotBlank() },
                    a.lastname?.takeIf { it.isNotBlank() }
                ).joinToString(" ")
            }?.takeIf { it.isNotBlank() } ?: "Unknown Author"

            Log.d(TAG, "Parsed: \"$title\" by $author, ${epub.spine.spineReferences.size} spine items")

            txtFile.bufferedWriter(Charsets.UTF_8).use { writer ->
                // Title header
                writer.write(title.uppercase())
                writer.newLine()
                writer.write("by $author")
                writer.newLine()
                writer.newLine()
                writer.write("─".repeat(40))
                writer.newLine()
                writer.newLine()

                val spineRefs = epub.spine.spineReferences
                for (spineRef in spineRefs) {
                    val resource: Resource = spineRef.resource ?: continue
                    try {
                        val xhtml = String(resource.data, Charsets.UTF_8)
                        val text = stripHtml(xhtml).trim()
                        if (text.isNotEmpty()) {
                            writer.write(text)
                            writer.newLine()
                            writer.newLine()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Skipping spine item ${resource.href}: ${e.message}")
                    }
                }
            }

            Log.d(TAG, "Conversion complete: ${txtFile.length()} bytes")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Conversion failed: ${e.message}", e)
            if (txtFile.exists()) txtFile.delete()
            false
        }
    }

    /**
     * Strips HTML tags and decodes common entities to produce plain text.
     */
    private fun stripHtml(html: String): String {
        // Extract body content if present
        val body = run {
            val start = html.indexOf("<body", ignoreCase = true)
            if (start == -1) return@run html
            val tagEnd = html.indexOf('>', start)
            if (tagEnd == -1) return@run html
            val end = html.indexOf("</body>", tagEnd, ignoreCase = true)
            if (end == -1) html.substring(tagEnd + 1) else html.substring(tagEnd + 1, end)
        }

        return body
            // Remove style/script blocks
            .replace(Regex("<style[^>]*>.*?</style>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
            .replace(Regex("<script[^>]*>.*?</script>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
            // Block elements get newlines
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</(p|div|h[1-6]|li|tr)>", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("<(p|div|h[1-6]|li|tr)[^>]*>", RegexOption.IGNORE_CASE), "")
            // Remove remaining tags
            .replace(Regex("<[^>]+>"), "")
            // Decode HTML entities
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace(Regex("&#(\\d+);")) { chr ->
                val code = chr.groupValues[1].toIntOrNull()
                if (code != null) String(charArrayOf(code.toChar())) else ""
            }
            // Clean up whitespace
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }
}
