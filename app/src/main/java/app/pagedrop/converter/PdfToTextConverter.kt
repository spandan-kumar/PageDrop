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
 * Extracts readable text from a PDF file and writes it as a plain .txt file.
 *
 * Uses PDFBox to extract text page by page.
 * Layout and images are NOT preserved — only text content.
 */
object PdfToTextConverter {

    private const val TAG = "PdfToTextConverter"

    suspend fun convert(context: Context, pdfFile: File, txtFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting PDF→TXT: ${pdfFile.name} → ${txtFile.name}")

            if (!PDFBoxResourceLoader.isReady()) {
                PDFBoxResourceLoader.init(context.applicationContext)
            }

            val document = PDDocument.load(pdfFile)
            document.use { doc ->
                val title = txtFile.nameWithoutExtension
                val numberOfPages = doc.numberOfPages
                Log.d(TAG, "PDF has $numberOfPages pages")

                txtFile.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(title.uppercase())
                    writer.newLine()
                    writer.newLine()
                    writer.write("─".repeat(40))
                    writer.newLine()
                    writer.newLine()

                    val stripper = PDFTextStripper()
                    for (page in 1..numberOfPages) {
                        stripper.startPage = page
                        stripper.endPage = page

                        val pageText = stripper.getText(doc)?.trim() ?: continue
                        if (pageText.isEmpty()) continue

                        writer.write(pageText)
                        writer.newLine()
                        writer.newLine()
                    }
                }

                Log.d(TAG, "Conversion complete: ${txtFile.length()} bytes")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Conversion failed: ${e.message}", e)
            if (txtFile.exists()) txtFile.delete()
            false
        }
    }
}
