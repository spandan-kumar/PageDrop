package app.pagedrop.converter

import android.content.Context
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Converts PDF files to MOBI format suitable for Kindle.
 *
 * Pipeline:
 *   1. Extract text from each PDF page using PDFBox
 *   2. Wrap extracted text in simple HTML (paragraphs split by double newlines,
 *      page breaks between PDF pages)
 *   3. Delegate binary MOBI writing to [MobiWriter]
 *
 * Note: Layout, images, and complex formatting are NOT preserved —
 * only the text content is extracted.
 */
object PdfToMobiConverter {

    private const val TAG = "PdfToMobiConverter"

    /**
     * Converts a PDF file to MOBI format.
     *
     * @param context Android context (needed for PDFBox resource initialization)
     * @param pdfFile source PDF file
     * @param mobiFile target MOBI file (will be created)
     * @return [ConversionResult] with success status and Kindle UUID (PDFs have no cover)
     */
    suspend fun convert(context: Context, pdfFile: File, mobiFile: File): ConversionResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting conversion: ${pdfFile.name} → ${mobiFile.name}")

            // Ensure PDFBox resources are initialized
            if (!PDFBoxResourceLoader.isReady()) {
                PDFBoxResourceLoader.init(context.applicationContext)
            }

            // 1. Load PDF document
            val document = PDDocument.load(pdfFile)
            document.use { doc ->
                val numberOfPages = doc.numberOfPages
                Log.d(TAG, "PDF has $numberOfPages pages")

                // 2. Extract title from output filename (not temp input file)
                val title = mobiFile.nameWithoutExtension

                // 3. Build HTML from extracted text
                val htmlContent = buildHtmlFromPdf(title, doc, numberOfPages)

                // 4. Write MOBI
                val writer = MobiWriter(
                    title = title,
                    author = "Unknown Author",
                    htmlContent = htmlContent.toByteArray(Charsets.UTF_8),
                )
                writer.write(mobiFile)

                Log.d(TAG, "Conversion complete: ${mobiFile.length()} bytes")
                ConversionResult(
                    success = true,
                    coverBytes = null,
                    kindleUuid = writer.kindleUuid
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Conversion failed: ${e.message}", e)
            if (mobiFile.exists()) mobiFile.delete()
            ConversionResult(success = false)
        }
    }

    /**
     * Builds a single HTML document from all pages in the PDF.
     * Each page's text is extracted separately, with paragraphs split by
     * double newlines and page breaks inserted between pages.
     */
    private fun buildHtmlFromPdf(title: String, document: PDDocument, numberOfPages: Int): String {
        val sb = StringBuilder(64 * 1024)
        sb.append("<html><head><title>")
        sb.append(escapeHtml(title))
        sb.append("</title></head><body>\n")

        val stripper = PDFTextStripper()

        for (page in 1..numberOfPages) {
            stripper.startPage = page
            stripper.endPage = page

            val pageText = stripper.getText(document)?.trim() ?: continue

            if (pageText.isEmpty()) continue

            // Split text into paragraphs by double newlines
            val paragraphs = pageText.split(Regex("\\n\\s*\\n"))
            for (paragraph in paragraphs) {
                val trimmed = paragraph.trim()
                if (trimmed.isNotEmpty()) {
                    sb.append("<p>")
                    sb.append(escapeHtml(trimmed).replace("\n", "<br/>"))
                    sb.append("</p>\n")
                }
            }

            // Add page break between PDF pages (not after the last one)
            if (page < numberOfPages) {
                sb.append("<mbp:pagebreak/>\n")
            }
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
